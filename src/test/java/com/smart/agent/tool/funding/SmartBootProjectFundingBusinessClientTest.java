package com.smart.agent.tool.funding;

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

class SmartBootProjectFundingBusinessClientTest {
    @Test void comparisonSignsItsOwnPathAndKeepsSeparateScopesDatesAndPrecision() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(),signature=new AtomicReference<>(),timestamp=new AtomicReference<>(),uri=new AtomicReference<>();
        DisposableServer server=HttpServer.create().port(0).handle((request,response)->{
            uri.set(request.uri());signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            return request.receive().aggregate().asString().flatMap(content->{body.set(content);
                return response.header("Content-Type","application/json").sendString(Mono.just(
                        "{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"currentValue\":123456789012345.67}],\"columns\":[],\"appliedFilters\":[]}")).then();});
        }).bindNow();
        try {
            var client=new SmartBootProjectFundingBusinessClient(WebClient.builder().baseUrl("http://127.0.0.1:"+server.port()).build(),new ObjectMapper(),"test-secret");
            var context=new ToolContext("tenant","user","identity",Set.of("role"),Set.of("p1","p2"),Set.of("menu:finance:payment-request"));
            var a=new ProjectFundingQueryInput("p1",null,null,"summary","net","projectCreateDate","2026-01-01","2026-01-31",1,20,null);
            var b=new ProjectFundingQueryInput(null,"乙",null,"summary","net","projectCreateDate","2026-02-01","2026-02-28",1,20,"contains");
            var result=client.compare(context,new ProjectFundingCompareInput("CUSTOM",a,b));
            assertThat(uri.get()).isEqualTo("/internal/ai/tools/project-funding-compare");
            var json=new ObjectMapper().readTree(body.get());
            assertThat(json.path("current").path("projectIds")).hasSize(1);assertThat(json.path("current").path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(json.path("baseline").path("projectIds")).hasSize(2);assertThat(json.path("baseline").path("projectName").asText()).isEqualTo("乙");
            assertThat(json.path("baseline").path("metric").asText()).isEqualTo("net");assertThat(json.path("baseline").path("endDate").asText()).isEqualTo("2026-02-28");
            assertThat(result.rows().getFirst().get("currentValue").toString()).isEqualTo("123456789012345.67");
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String canonical=timestamp.get()+"\nPOST\n/internal/ai/tools/project-funding-compare\ntenant\nuser\nidentity\nrole\nmenu:finance:payment-request\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } finally {server.disposeNow();}
    }
    @Test void deniedSecondGroupNeverSendsRequest() {
        var client=new SmartBootProjectFundingBusinessClient(WebClient.builder().exchangeFunction(r->{throw new AssertionError("No request permitted");}).build(),new ObjectMapper(),"secret");
        var a=new ProjectFundingQueryInput("p1",null,null,null,null,null,null,null,null,null,null);
        var b=new ProjectFundingQueryInput("outside",null,null,null,null,null,null,null,null,null,null);
        assertThatThrownBy(()->client.compare(new ToolContext("t","u","i",Set.of("p1")),new ProjectFundingCompareInput("CUSTOM",a,b))).isInstanceOf(SecurityException.class);
    }
    @Test void missingAmountResponseIsNotMisreportedAsInvalidFilter() {
        var client=new SmartBootProjectFundingBusinessClient(WebClient.builder().exchangeFunction(r->Mono.just(
                org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).build())).build(),new ObjectMapper(),"secret");
        var a=new ProjectFundingQueryInput(null,null,null,null,null,null,null,null,null,null,null);
        assertThatThrownBy(()->client.compare(new ToolContext("t","u","i",Set.of("p1")),new ProjectFundingCompareInput("CUSTOM",a,a)))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).hasMessageContaining("金额").hasMessageNotContaining("参数无效");
    }
    @Test void forbiddenResponseKeepsTheFinancialPermissionError() {
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(
                org.springframework.web.reactive.function.client.ClientResponse
                        .create(org.springframework.http.HttpStatus.FORBIDDEN).build())).build();
        var business = new SmartBootProjectFundingBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new ProjectFundingQueryInput(null, null, null, "summary", "funding", null, null, null, 1, 20, null)))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class)
                .extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
    }

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
                        "{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"registrationCount\":2,\"fundingAmount\":123456789012345.67}],\"columns\":[{\"key\":\"fundingAmount\",\"label\":\"项目垫资总金额\",\"type\":\"number\"}],\"appliedFilters\":[]}"))
                        .then();
            });
        }).bindNow();
        try {
            var client = new SmartBootProjectFundingBusinessClient(
                    WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(), new ObjectMapper(), "test-secret");
            var context = new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of("p2", "p1"), Set.of("menu:finance:payment-request"));
            var input = new ProjectFundingQueryInput("p1", "测试项目", null, "summary", "funding", "projectCreateDate",
                    "2026-01-01", "2026-01-31", 1, 20, null);
            var result = client.query(context, input);
            assertThat(uri.get()).isEqualTo("/internal/ai/tools/project-funding-query");
            assertThat(new ObjectMapper().readTree(body.get()).path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(new ObjectMapper().readTree(body.get()).path("dateField").asText()).isEqualTo("projectCreateDate");
            assertThat(result.rows().getFirst().get("fundingAmount").toString()).isEqualTo("123456789012345.67");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp.get() + "\nPOST\n/internal/ai/tools/project-funding-query\ntenant\nuser\nidentity\nrole\nmenu:finance:payment-request\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } finally { server.disposeNow(); }
    }

    @Test void explicitlyUnauthorizedProjectNeverMakesAnHttpRequest() {
        WebClient client = WebClient.builder().exchangeFunction(request -> { throw new AssertionError("must not call"); }).build();
        var business = new SmartBootProjectFundingBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new ProjectFundingQueryInput("denied", null, null, "summary", "funding", null, null, null, 1, 20, null)))
                .isInstanceOf(SecurityException.class);
    }
}
