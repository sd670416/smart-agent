package com.smart.agent.tool.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.common.error.AgentException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class SmartBootProjectBusinessClientTest {
    private static final String SECRET = "test-secret";

    @Test
    void doesNotSerializeRawDictionaryCodesForModelToolResults() throws Exception {
        ProjectOverviewResult result = new ProjectOverviewResult(
                "p-1", "项目一", "1", "在建", 50D, "2", "P001", "3", "房建", null);

        String json = new ObjectMapper().writeValueAsString(result);

        assertThat(json).contains("\"statusName\":\"在建\"", "\"projectTypeName\":\"房建\"")
                .doesNotContain("\"status\":", "\"approvalStatus\":", "\"projectType\":");
    }

    @Test
    void signsProjectOverviewRequests() throws Exception {
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> roles = new AtomicReference<>();
        AtomicReference<String> permissions = new AtomicReference<>();
        AtomicReference<String> projects = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));
            roles.set(request.requestHeaders().get("X-Agent-Role-Ids"));
            permissions.set(request.requestHeaders().get("X-Agent-Permissions"));
            projects.set(request.requestHeaders().get("X-Agent-Project-Ids"));
            return response.header("Content-Type", "application/json")
                    .sendString(reactor.core.publisher.Mono.just(
                            "{\"projectId\":\"p-1\",\"projectName\":\"上德项目一\",\"status\":\"1\",\"statusName\":\"在建\","
                                    + "\"approvalStatus\":\"2\",\"projectCode\":\"SD001\","
                                    + "\"projectType\":\"1\",\"projectTypeName\":\"房建\",\"projectBudget\":300000}"));
        }).bindNow();
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port()).build();
            SmartBootProjectBusinessClient client =
                    new SmartBootProjectBusinessClient(webClient, new ObjectMapper(), SECRET);

            ProjectOverviewResult result = client.getOverview(
                    new ToolContext("tenant", "user", "identity", Set.of("role-b", "role-a"), Set.of("p-1"),
                            Set.of("menu:project:contract", "menu:project:base")), "p-1");

            assertThat(result.projectName()).isEqualTo("上德项目一");
            assertThat(result.statusName()).isEqualTo("在建");
            assertThat(result.projectCode()).isEqualTo("SD001");
            assertThat(result.projectBudget()).isEqualByComparingTo("300000");
            assertThat(result.projectTypeName()).isEqualTo("房建");
            assertThat(timestamp.get()).isNotBlank();
            assertThat(roles.get()).isEqualTo("role-a,role-b");
            assertThat(permissions.get()).isEqualTo("menu:project:base,menu:project:contract");
            assertThat(projects.get()).isEqualTo("p-1");
            assertThat(signature.get()).isEqualTo(sign(timestamp.get(), "POST", "/internal/ai/tools/project-overview",
                    "tenant", "user", "identity", roles.get(), permissions.get(), projects.get()));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void injectsTrustedProjectIdsOnlyInInternalQueryRequest() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) ->
                request.receive().aggregate().asString().flatMap(content -> {
                    body.set(content);
                    return response.header("Content-Type", "application/json").sendString(
                            reactor.core.publisher.Mono.just("{\"mode\":\"DETAIL\",\"page\":1,\"pageSize\":20,"
                                    + "\"total\":0,\"hasNext\":false,\"columns\":[],\"rows\":[],"
                                    + "\"appliedFilters\":[]}" )).then();
                })).bindNow();
        try {
            SmartBootProjectBusinessClient client = new SmartBootProjectBusinessClient(
                    WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(),
                    new ObjectMapper(), SECRET);
            ProjectQueryInput input = new ProjectQueryInput(List.of("projectName"), null,
                    List.of(), List.of(), List.of(), 1, 20);

            ProjectQueryResult result = client.query(
                    new ToolContext("tenant", "user", "identity", Set.of("p1", "p2")), input);

            assertThat(result.total()).isZero();
            assertThat(body.get()).contains("\"projectIds\"", "\"p1\"", "\"p2\"", "\"select\":[\"projectName\"]")
                    .doesNotContain("tenantId", "userId", "identityId");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void emptyTrustedScopeReturnsEmptyWithoutHttpRequest() {
        SmartBootProjectBusinessClient client = new SmartBootProjectBusinessClient(
                WebClient.builder().baseUrl("http://127.0.0.1:1").build(), new ObjectMapper(), SECRET);

        ProjectQueryResult result = client.query(
                new ToolContext("tenant", "user", "identity", Set.of()),
                new ProjectQueryInput(List.of("projectName"), null, List.of(), List.of(), List.of(), 1, 20));

        assertThat(result.total()).isZero();
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void preservesSmartBootQueryValidationError() {
        DisposableServer server = HttpServer.create().port(0).handle((request, response) ->
                response.status(400).header("Content-Type", "application/json")
                        .sendString(reactor.core.publisher.Mono.just(
                                "{\"code\":\"PROJECT_QUERY_VALUE_INVALID\",\"message\":\"不支持的项目状态：立项\"}"))).bindNow();
        try {
            SmartBootProjectBusinessClient client = new SmartBootProjectBusinessClient(
                    WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(),
                    new ObjectMapper(), SECRET);

            assertThatThrownBy(() -> client.query(
                    new ToolContext("tenant", "user", "identity", Set.of("p1")),
                    new ProjectQueryInput(List.of("projectName"), null, List.of(), List.of(), List.of(), 1, 20)))
                    .isInstanceOf(AgentException.class)
                    .satisfies(error -> {
                        AgentException agentError = (AgentException) error;
                        assertThat(agentError.code()).isEqualTo("AGENT_QUERY_VALUE_INVALID");
                        assertThat(agentError.getMessage()).isEqualTo("不支持的项目状态：立项");
                    });
        } finally {
            server.disposeNow();
        }
    }

    private String sign(String timestamp, String method, String path, String tenantId, String userId,
                        String identityId, String roles, String permissions, String projects) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] content = (timestamp + "\n" + method + "\n" + path + "\n" + tenantId + "\n" + userId
                + "\n" + identityId + "\n" + roles + "\n" + permissions + "\n" + projects)
                .getBytes(StandardCharsets.UTF_8);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content));
    }
}
