package com.smart.agent.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.common.trace.TraceIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AgentContextFilterTest {
    private static final String LOCAL_SECRET = "test-secret-with-enough-entropy";
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private MockMvc mvc;
    private ContextTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = mock(ContextTokenVerifier.class);
        ObjectMapper objectMapper = new ObjectMapper();
        mvc = MockMvcBuilders.standaloneSetup(new TestContextController())
                .addFilters(new TraceIdFilter(), new AgentContextFilter(verifier, objectMapper))
                .build();
    }

    @Test
    void rejectsMalformedContextTokenWithStableError() throws Exception {
        mvc = mvcWith(new LocalContextTokenVerifier(new ObjectMapper(), LOCAL_SECRET, FIXED_CLOCK));

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, "not-a-compact-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid agent context is required"));
    }

    @Test
    void rejectsExpiredContextTokenWithStableError() throws Exception {
        mvc = mvcWith(new LocalContextTokenVerifier(new ObjectMapper(), LOCAL_SECRET, FIXED_CLOCK));
        String expiredToken = signedToken(Instant.parse("2026-09-01T00:59:59Z").getEpochSecond());

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid agent context is required"));
    }

    @Test
    void rejectsContextTokenWithForgedSignature() throws Exception {
        mvc = mvcWith(new LocalContextTokenVerifier(new ObjectMapper(), LOCAL_SECRET, FIXED_CLOCK));
        String validToken = signedToken(Instant.parse("2026-09-01T01:05:00Z").getEpochSecond());
        int signatureStart = validToken.indexOf('.') + 1;
        char replacement = validToken.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String forgedToken = validToken.substring(0, signatureStart)
                + replacement
                + validToken.substring(signatureStart + 1);

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid agent context is required"));
    }

    @Test
    void rejectsUnexpectedVerifierFailureWithStableError() throws Exception {
        when(verifier.verify("signed-token")).thenThrow(new IllegalStateException("internal verifier detail"));

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, "signed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid agent context is required"));
    }

    @Test
    void doesNotExposeVerifierExceptionDetails() throws Exception {
        when(verifier.verify("signed-token")).thenThrow(new AgentException(
                "VERIFIER_FAILURE", HttpStatus.INTERNAL_SERVER_ERROR, "token or secret detail"));

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, "signed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid agent context is required"));
    }

    @Test
    void acceptsValidSignedTokenAndIgnoresForgedClaims() throws Exception {
        mvc = mvcWith(new LocalContextTokenVerifier(new ObjectMapper(), LOCAL_SECRET, FIXED_CLOCK));
        String token = signedToken(Instant.parse("2026-09-01T01:05:00Z").getEpochSecond());

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, token)
                        .header("X-Tenant-Id", "forged-tenant-header")
                        .header("X-User-Id", "forged-user-header")
                        .header("X-Identity-Id", "forged-identity-header")
                        .header("X-Agent-Permissions", "admin:*")
                        .header("X-Agent-Projects", "forged-project-header")
                        .param("tenantId", "forged-tenant-param")
                        .param("userId", "forged-user-param")
                        .param("identityId", "forged-identity-param")
                        .param("permissions", "admin:*")
                        .param("projectIds", "forged-project-param"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.identityId").value("identity-1"))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("project:read")))
                .andExpect(jsonPath("$.projectIds", containsInAnyOrder("project-1")));
    }

    @Test
    void userContextDefensivelyCopiesTrustedScopeSets() {
        Set<String> permissions = new HashSet<>(Set.of("project:read"));
        Set<String> projectIds = new HashSet<>(Set.of("project-1"));
        AgentUserContext context =
                new AgentUserContext("tenant-1", "user-1", "identity-1", permissions, projectIds);

        permissions.clear();
        projectIds.clear();

        assertThat(context.permissions()).containsExactly("project:read");
        assertThat(context.canAccessProject("project-1")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0123456789abcdef0123456789ABCDEF",
        "123e4567-e89b-12d3-a456-426614174000"
    })
    void propagatesOnlyStrictValidIncomingTraceIds(String incomingTraceId) throws Exception {
        mvc.perform(get("/agent/internal/test-context")
                        .header(TraceIdFilter.TRACE_ID_HEADER, incomingTraceId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, incomingTraceId))
                .andExpect(jsonPath("$.traceId").value(incomingTraceId));
    }

    @Test
    void authenticatesAsyncDispatchAndClearsHolderAfterward() throws Exception {
        AgentUserContext context = new AgentUserContext(
                "tenant-1", "user-1", "identity-1", Set.of("project:read"), Set.of("project-1"));
        AgentContextFilter filter = new AgentContextFilter(token -> context, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/agent/internal/test-context");
        request.setDispatcherType(DispatcherType.ASYNC);
        request.addHeader(AgentContextFilter.CONTEXT_HEADER, "signed-token");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            assertThat(AgentContextHolder.requireContext()).isSameAs(context);
            assertThat(servletRequest.getAttribute(AgentUserContext.class.getName())).isSameAs(context);
        });

        assertThat(AgentContextHolder.current()).isEmpty();
        assertThat(request.getAttribute(AgentUserContext.class.getName())).isNull();
    }

    @Test
    void authenticatesErrorDispatchAndClearsHolderAfterward() throws Exception {
        AgentUserContext context = new AgentUserContext(
                "tenant-1", "user-1", "identity-1", Set.of("project:read"), Set.of("project-1"));
        AgentContextFilter filter = new AgentContextFilter(token -> context, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/agent/internal/test-context");
        request.setDispatcherType(DispatcherType.ERROR);
        request.addHeader(AgentContextFilter.CONTEXT_HEADER, "signed-token");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            assertThat(AgentContextHolder.requireContext()).isSameAs(context);
            assertThat(servletRequest.getAttribute(AgentUserContext.class.getName())).isSameAs(context);
        });

        assertThat(AgentContextHolder.current()).isEmpty();
        assertThat(request.getAttribute(AgentUserContext.class.getName())).isNull();
    }

    @Test
    void rejectsSignedTokenWithMissingRequiredContextClaims() throws Exception {
        mvc = mvcWith(new LocalContextTokenVerifier(new ObjectMapper(), LOCAL_SECRET, FIXED_CLOCK));
        String token = signedToken("", Instant.parse("2026-09-01T01:05:00Z").getEpochSecond());

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0123456789abcdef0123456789abcdeG",
        "123e4567e89b-12d3-a456-426614174000",
        "123e4567-e89b-12d3-a456-4266141740000"
    })
    void replacesMalformedIncomingTraceIds(String incomingTraceId) throws Exception {
        String generatedTraceId = mvc.perform(get("/agent/internal/test-context")
                        .header(TraceIdFilter.TRACE_ID_HEADER, incomingTraceId))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getHeader(TraceIdFilter.TRACE_ID_HEADER);

        assertThat(generatedTraceId).isNotEqualTo(incomingTraceId).matches(UUID_PATTERN);
    }

    @Test
    void clearsHolderAndRequestAttributeWhenDownstreamFails() {
        AgentUserContext context = new AgentUserContext(
                "tenant-1", "user-1", "identity-1", Set.of("project:read"), Set.of("project-1"));
        AgentContextFilter filter = new AgentContextFilter(token -> context, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/agent/internal/test-context");
        request.addHeader(AgentContextFilter.CONTEXT_HEADER, "signed-token");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                        (servletRequest, servletResponse) -> {
                            assertThat(AgentContextHolder.requireContext()).isSameAs(context);
                            throw new ServletException("downstream failure");
                        }))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream failure");
        assertThat(AgentContextHolder.current()).isEmpty();
        assertThat(request.getAttribute(AgentUserContext.class.getName())).isNull();
    }

    @Test
    void clearsStaleHolderWhenAuthenticationFails() throws Exception {
        AgentUserContext staleContext = new AgentUserContext(
                "stale-tenant", "stale-user", "stale-identity", Set.of(), Set.of());
        AgentContextHolder.set(staleContext);
        try {
            mvc.perform(get("/agent/internal/test-context"))
                    .andExpect(status().isUnauthorized());

            assertThat(AgentContextHolder.current()).isEmpty();
        } finally {
            AgentContextHolder.clear();
        }
    }

    @Test
    void exposesVerifiedContextWithoutTrustingRequestParametersAndClearsHolder() throws Exception {
        when(verifier.verify("signed-token")).thenReturn(new AgentUserContext(
                "tenant-1", "user-1", "identity-1", Set.of("project:read"), Set.of("project-1")));

        mvc.perform(get("/agent/internal/test-context")
                        .header(AgentContextFilter.CONTEXT_HEADER, "signed-token")
                        .param("tenantId", "forged-tenant")
                        .param("userId", "forged-user")
                        .param("permissions", "admin:*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.holderTenantId").value("tenant-1"));

        assertThat(AgentContextHolder.current()).isEmpty();
    }

    @Test
    void rejectsMissingContextTokenWithStableErrorAndGeneratedTrace() throws Exception {
        String traceId = mvc.perform(get("/agent/internal/test-context"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid agent context is required"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andReturn()
                .getResponse()
                .getHeader(TraceIdFilter.TRACE_ID_HEADER);

        assertThat(traceId).matches(UUID_PATTERN);
    }

    @Test
    void leavesRoutesOutsideAgentNamespaceUnprotected() throws Exception {
        mvc.perform(get("/internal/test-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("public"));
    }

    @RestController
    static class TestContextController {
        @GetMapping("/agent/internal/test-context")
        Map<String, Object> context(HttpServletRequest request) {
            AgentUserContext requestContext =
                    (AgentUserContext) request.getAttribute(AgentUserContext.class.getName());
            AgentUserContext holderContext = AgentContextHolder.requireContext();
            return Map.of(
                    "tenantId", requestContext.tenantId(),
                    "userId", requestContext.userId(),
                    "identityId", requestContext.identityId(),
                    "permissions", requestContext.permissions(),
                    "projectIds", requestContext.projectIds(),
                    "holderTenantId", holderContext.tenantId());
        }

        @GetMapping("/internal/test-context")
        Map<String, Object> publicContext() {
            return Map.of("status", "public");
        }
    }

    private static MockMvc mvcWith(ContextTokenVerifier contextTokenVerifier) {
        return MockMvcBuilders.standaloneSetup(new TestContextController())
                .addFilters(new TraceIdFilter(), new AgentContextFilter(contextTokenVerifier, new ObjectMapper()))
                .build();
    }

    private static String signedToken(long expiresAtEpochSecond) throws Exception {
        return signedToken("tenant-1", expiresAtEpochSecond);
    }

    private static String signedToken(String tenantId, long expiresAtEpochSecond) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        byte[] payload = mapper.writeValueAsBytes(Map.of(
                "tenantId", tenantId,
                "userId", "user-1",
                "identityId", "identity-1",
                "permissions", Set.of("project:read"),
                "projectIds", Set.of("project-1"),
                "exp", expiresAtEpochSecond));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(LOCAL_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        return encodedPayload + "." + encodedSignature;
    }
}
