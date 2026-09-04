package com.smart.agent.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LocalContextTokenVerifierContractTest {
    private static final String SECRET = "test-signing-secret";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1700000100L), ZoneOffset.UTC);

    @Test
    void acceptsBootCompatibleThreeSegmentContextAndBuildsRoleContext() throws Exception {
        AgentUserContext context = verifier().verify(token(List.of("role-b", "role-a"), 1700000300L));

        assertThat(context.tenantId()).isEqualTo("tenant-1");
        assertThat(context.userId()).isEqualTo("user-1");
        assertThat(context.identityId()).isEqualTo("identity-1");
        assertThat(context.roleIds()).containsExactlyInAnyOrder("role-a", "role-b");
        assertThat(context.permissions()).isEmpty();
        assertThat(context.projectIds()).isEmpty();
        assertThat(context.knowledgeSpaceIds()).isEmpty();
    }

    @Test
    void rejectsBootContextWithoutRoles() throws Exception {
        assertThatThrownBy(() -> verifier().verify(token(List.of(), 1700000300L)))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void acceptsSignedPermissionsAndProjectIds() throws Exception {
        AgentUserContext context = verifier().verify(token(
                List.of("role-a"), List.of("ai:knowledge:upload"), List.of("project-1"), 1700000300L));

        assertThat(context.permissions()).containsExactly("ai:knowledge:upload");
        assertThat(context.projectIds()).containsExactly("project-1");
    }

    private LocalContextTokenVerifier verifier() {
        return new LocalContextTokenVerifier(new ObjectMapper(), SECRET, CLOCK);
    }

    private String token(List<String> roleIds, long expiresAt) throws Exception {
        return token(roleIds, null, null, expiresAt);
    }

    private String token(List<String> roleIds, List<String> permissions, List<String> projectIds,
            long expiresAt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("tenantId", "tenant-1");
        claims.put("userId", "user-1");
        claims.put("identityId", "identity-1");
        claims.put("roleIds", roleIds);
        if (permissions != null) claims.put("permissions", permissions);
        if (projectIds != null) claims.put("projectIds", projectIds);
        claims.put("issuedAt", 1700000000L);
        claims.put("expiresAt", expiresAt);
        claims.put("nonce", "nonce-1");
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(mapper.writeValueAsString(claims));
        String unsigned = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
