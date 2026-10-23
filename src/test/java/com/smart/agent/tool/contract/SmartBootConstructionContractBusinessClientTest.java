package com.smart.agent.tool.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;

class SmartBootConstructionContractBusinessClientTest {
    @Test void queriesSignedEndpointWithTrustedScopeAndReturnsPreciseAmounts() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> uri = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            uri.set(request.uri());
            signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));
            timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            assertThat(request.requestHeaders().get("X-Agent-Project-Ids")).isEqualTo("p1,p2");
            return request.receive().aggregate().asString().flatMap(content -> {
                body.set(content);
                return response.header("Content-Type", "application/json").sendString(Mono.just(
                        "{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"contractCount\":2,\"finalContractAmount\":123456789012345.67}],\"columns\":[{\"key\":\"finalContractAmount\",\"label\":\"建设合同总金额\",\"type\":\"number\"}],\"appliedFilters\":[]}"))
                        .then();
            });
        }).bindNow();
        try {
            var client = new SmartBootConstructionContractBusinessClient(
                    WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(), new ObjectMapper(), "test-secret");
            var context = new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of("p2", "p1"), Set.of("menu:project"));
            var input = new ConstructionContractQueryInput("p1", "测试项目", null, "summary", "signingDate",
                    "2026-01-01", "2026-01-31", 1, 20, null, "exact", "projectType", "1", "土建");
            var result = client.query(context, input);
            assertThat(uri.get()).isEqualTo("/internal/ai/tools/construction-contract-query");
            assertThat(new ObjectMapper().readTree(body.get()).path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(new ObjectMapper().readTree(body.get()).path("dateField").asText()).isEqualTo("signingDate");
            assertThat(new ObjectMapper().readTree(body.get()).path("groupBy").asText()).isEqualTo("projectType");
            assertThat(new ObjectMapper().readTree(body.get()).path("projectType").asText()).isEqualTo("1");
            assertThat(new ObjectMapper().readTree(body.get()).path("projectTypeName").asText()).isEqualTo("土建");
            assertThat(result.rows().getFirst().get("finalContractAmount").toString()).isEqualTo("123456789012345.67");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp.get() + "\nPOST\n/internal/ai/tools/construction-contract-query\ntenant\nuser\nidentity\nrole\nmenu:project\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
            client.query(context, new ObjectMapper().readValue("{\"projectId\":\"p1\",\"contractId\":\"c1\"}", ConstructionContractQueryInput.class));
            assertThat(new ObjectMapper().readTree(body.get()).path("contractId").asText()).isEqualTo("c1");
            assertThat(new ObjectMapper().readTree(body.get()).path("mode").asText()).isEqualTo("detail");
            client.query(context, new ObjectMapper().readValue("{\"projectId\":\"p1\",\"contractId\":\"c1\",\"mode\":\"records\",\"recordType\":\"change\",\"page\":2}", ConstructionContractQueryInput.class));
            var recordRequest = new ObjectMapper().readTree(body.get());
            assertThat(recordRequest.path("contractId").asText()).isEqualTo("c1");
            assertThat(recordRequest.path("recordType").asText()).isEqualTo("change");
            assertThat(recordRequest.path("page").asInt()).isEqualTo(2);
        } finally { server.disposeNow(); }
    }

    @Test void groupedComparisonTransmitsBothGroupsAndPreciseValues() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>(), timestamp = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request,response) -> {
            assertThat(request.uri()).isEqualTo("/internal/ai/tools/construction-contract-compare");
            signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));
            timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            return request.receive().aggregate().asString().flatMap(content -> {
                body.set(content);
                return response.header("Content-Type","application/json").sendString(Mono.just(
                        "{\"mode\":\"aggregate\",\"rows\":[{\"projectType\":\"1\",\"metric\":\"建设合同总金额\",\"currentValue\":123456789012345.67,\"growthPercent\":null}],\"columns\":[],\"appliedFilters\":[]}")).then();
            });
        }).bindNow();
        try {
            var mapper = new ObjectMapper();
            var client = new SmartBootConstructionContractBusinessClient(WebClient.builder().baseUrl("http://127.0.0.1:"+server.port()).build(),mapper,"secret");
            var current = mapper.readValue("{\"mode\":\"summary\",\"groupBy\":\"projectType\",\"projectId\":\"p1\",\"projectTypeName\":\"土建\",\"dateField\":\"signingDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}",ConstructionContractQueryInput.class);
            var baseline = mapper.readValue("{\"mode\":\"summary\",\"groupBy\":\"projectType\",\"projectId\":\"p2\",\"dateField\":\"signingDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}",ConstructionContractQueryInput.class);
            var result = client.compare(new ToolContext("t","u","i",Set.of(),Set.of("p1","p2"),Set.of("menu:project")),
                    new ConstructionContractCompareInput("CUSTOM",null,current,baseline));
            var sent = mapper.readTree(body.get());
            assertThat(sent.path("current").path("groupBy").asText()).isEqualTo("projectType");
            assertThat(sent.path("baseline").path("groupBy").asText()).isEqualTo("projectType");
            assertThat(sent.path("current").path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(sent.path("baseline").path("projectIds").get(0).asText()).isEqualTo("p2");
            assertThat(sent.path("current").path("projectTypeName").asText()).isEqualTo("土建");
            assertThat(sent.path("baseline").path("startDate").asText()).isEqualTo("2025-01-01");
            assertThat(result.rows().getFirst().get("currentValue").toString()).isEqualTo("123456789012345.67");
            assertThat(result.rows().getFirst().get("growthPercent")).isNull();
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String canonical=timestamp.get()+"\nPOST\n/internal/ai/tools/construction-contract-compare\nt\nu\ni\n\nmenu:project\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } finally {server.disposeNow();}
    }
    @Test void comparisonSignsItsOwnPathAndRejectsUnauthorizedBaseline() throws Exception {
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> captured = new AtomicReference<>();
        var client = new SmartBootConstructionContractBusinessClient(WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(org.springframework.web.reactive.function.client.ClientResponse
                    .create(org.springframework.http.HttpStatus.OK).header("Content-Type", "application/json")
                    .body("{\"mode\":\"aggregate\",\"rows\":[],\"columns\":[],\"appliedFilters\":[]}").build());
        }).build(), new ObjectMapper(), "test-secret");
        var context = new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of("p1"), Set.of("menu:project"));
        var current = new ConstructionContractQueryInput("p1", null, null, "summary", "signingDate", "2026-09-01", "2026-09-30", 1, 20);
        client.compare(context, new ConstructionContractCompareInput("YOY", "MONTH", current, null));
        var request = captured.get();
        assertThat(request.url().getPath()).isEqualTo("/internal/ai/tools/construction-contract-compare");
        String timestamp = request.headers().getFirst("X-Agent-Internal-Timestamp");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String canonical = timestamp + "\nPOST\n/internal/ai/tools/construction-contract-compare\ntenant\nuser\nidentity\nrole\nmenu:project\np1";
        assertThat(request.headers().getFirst("X-Agent-Internal-Signature")).isEqualTo(
                Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        captured.set(null);
        var denied = new ConstructionContractQueryInput("denied", null, null, "summary", "signingDate", null, null, 1, 20);
        assertThatThrownBy(() -> client.compare(context, new ConstructionContractCompareInput("CUSTOM", null, current, denied)))
                .isInstanceOf(SecurityException.class);
        assertThat(captured.get()).isNull();
    }

    @Test void explicitlyUnauthorizedProjectNeverMakesAnHttpRequest() {
        WebClient client = WebClient.builder().exchangeFunction(request -> { throw new AssertionError("must not call"); }).build();
        var business = new SmartBootConstructionContractBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new ConstructionContractQueryInput("denied", null, null, "summary", null, null, null, 1, 20)))
                .isInstanceOf(SecurityException.class);
    }
}
