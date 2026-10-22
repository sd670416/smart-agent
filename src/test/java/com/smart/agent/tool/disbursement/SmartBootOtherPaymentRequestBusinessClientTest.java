package com.smart.agent.tool.disbursement;

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

class SmartBootOtherPaymentRequestBusinessClientTest {
    @Test void unauthorizedBaselineNeverMakesAnHttpRequest() {
        WebClient client = WebClient.builder().exchangeFunction(request -> { throw new AssertionError("must not call"); }).build();
        var business = new SmartBootOtherPaymentRequestBusinessClient(client,new ObjectMapper(),"test-secret");
        var current = new OtherPaymentRequestQueryInput("p1",null,null,"summary","paymentDate","2026-01-01","2026-01-31",1,20);
        var baseline = new OtherPaymentRequestQueryInput("denied",null,null,"summary","paymentDate","2025-01-01","2025-01-31",1,20);
        assertThatThrownBy(()->business.compare(new ToolContext("tenant","user","identity",Set.of("p1")),
                new OtherPaymentRequestCompareInput("CUSTOM",null,current,baseline))).isInstanceOf(SecurityException.class);
    }
    @Test void forbiddenResponseKeepsTheFinancialPermissionError() {
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(
                org.springframework.web.reactive.function.client.ClientResponse
                        .create(org.springframework.http.HttpStatus.FORBIDDEN).build())).build();
        var business = new SmartBootOtherPaymentRequestBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new OtherPaymentRequestQueryInput(null, null, null, "summary", null, null, null, 1, 20)))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class)
                .extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"QUERY","YOY","CUSTOM"})
    void queriesSignedEndpointWithTrustedScopeAndReturnsPreciseAmounts(String mode) throws Exception {
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
                        "{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"registrationCount\":2,\"paymentAmount\":123456789012345.67}],\"columns\":[{\"key\":\"paymentAmount\",\"label\":\"其他付款申请总金额\",\"type\":\"number\"}],\"appliedFilters\":[]}"))
                        .then();
            });
        }).bindNow();
        try {
            var client = new SmartBootOtherPaymentRequestBusinessClient(
                    WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(), new ObjectMapper(), "test-secret");
            var context = new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of("p2", "p1"), Set.of("menu:finance:other-payment-request"));
            var input = new OtherPaymentRequestQueryInput("p1", "测试项目", null, "summary", "createDate",
                    "2026-01-01", "2026-01-31", 1, 20);
            var baseline = new OtherPaymentRequestQueryInput("p2",null,null,"summary","createDate","2025-01-01","2025-01-31",1,20);
            var result = "QUERY".equals(mode) ? client.query(context,input) : client.compare(context,
                    new OtherPaymentRequestCompareInput(mode,"MONTH",input,"CUSTOM".equals(mode)?baseline:null));
            String path="/internal/ai/tools/other-payment-request-"+("QUERY".equals(mode)?"query":"compare");
            assertThat(uri.get()).isEqualTo(path);
            var payload=new ObjectMapper().readTree(body.get());
            var group="QUERY".equals(mode)?payload:payload.path("current");
            assertThat(group.path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(group.path("dateField").asText()).isEqualTo("createDate");
            if("CUSTOM".equals(mode)) assertThat(payload.path("baseline").path("projectIds").get(0).asText()).isEqualTo("p2");
            if("YOY".equals(mode)) assertThat(payload.has("baseline")).isFalse();
            assertThat(result.rows().getFirst().get("paymentAmount").toString()).isEqualTo("123456789012345.67");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp.get() + "\nPOST\n"+path+"\ntenant\nuser\nidentity\nrole\nmenu:finance:other-payment-request\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } finally { server.disposeNow(); }
    }

    @Test void explicitlyUnauthorizedProjectNeverMakesAnHttpRequest() {
        WebClient client = WebClient.builder().exchangeFunction(request -> { throw new AssertionError("must not call"); }).build();
        var business = new SmartBootOtherPaymentRequestBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new OtherPaymentRequestQueryInput("denied", null, null, "summary", null, null, null, 1, 20)))
                .isInstanceOf(SecurityException.class);
    }
}
