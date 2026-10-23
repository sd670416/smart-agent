package com.smart.agent.tool.budget;

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

class SmartBootBudgetCostBusinessClientTest {
    @Test void comparisonSignsItsOwnPathAndCarriesSeparateGroupsWithoutLosingPrecision() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(),signature=new AtomicReference<>(),timestamp=new AtomicReference<>(),uri=new AtomicReference<>();
        var server=HttpServer.create().port(0).handle((request,response)->{
            uri.set(request.uri());signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            return request.receive().aggregate().asString().flatMap(content->{body.set(content);return response.header("Content-Type","application/json")
                    .sendString(Mono.just("{\"mode\":\"aggregate\",\"total\":2,\"rows\":[{\"currentValue\":123456789012345.67}],\"columns\":[],\"appliedFilters\":[]}")).then();});
        }).bindNow();
        try {
            var client=new SmartBootBudgetCostBusinessClient(WebClient.builder().baseUrl("http://127.0.0.1:"+server.port()).build(),new ObjectMapper(),"test-secret");
            var context=new ToolContext("t","u","i",Set.of("role"),Set.of("p1","p2"),Set.of("menu:budget:cost"));
            var first=new BudgetCostQueryInput("p1",null,null,null,"projectCreateDate","2026-01-01","2026-01-31",null,null,null);
            var second=new BudgetCostQueryInput(null,"二期",null,null,"projectCreateDate","2026-02-01","2026-02-28",null,null,"contains");
            var result=client.compare(context,new BudgetCostCompareInput("CUSTOM",first,second));
            assertThat(uri.get()).isEqualTo("/internal/ai/tools/budget-cost-compare");
            var json=new ObjectMapper().readTree(body.get());
            assertThat(json.path("current").path("projectIds").size()).isEqualTo(1);
            assertThat(json.path("current").path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(json.path("baseline").path("projectIds").size()).isEqualTo(2);
            assertThat(json.path("baseline").path("projectName").asText()).isEqualTo("二期");
            assertThat(json.path("baseline").path("endDate").asText()).isEqualTo("2026-02-28");
            assertThat(result.rows().getFirst().get("currentValue").toString()).isEqualTo("123456789012345.67");
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String canonical=timestamp.get()+"\nPOST\n/internal/ai/tools/budget-cost-compare\nt\nu\ni\nrole\nmenu:budget:cost\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } finally {server.disposeNow();}
    }
    @Test void secondGroupOutsideTrustedScopeNeverSendsHttpRequest() {
        var client=new SmartBootBudgetCostBusinessClient(WebClient.builder().exchangeFunction(r->{throw new AssertionError("must not call");}).build(),new ObjectMapper(),"s");
        var first=new BudgetCostQueryInput("p1",null,null,null,null,null,null,null,null,null);
        assertThatThrownBy(()->client.compare(new ToolContext("t","u","i",Set.of("p1")),new BudgetCostCompareInput("CUSTOM",first,first.withProjectId("outside"))))
                .isInstanceOf(SecurityException.class);
    }
    @Test void incompleteBudgetAmountsAreExplainedWithoutCallingThemInvalidFilters() {
        var http=WebClient.builder().exchangeFunction(request->Mono.just(org.springframework.web.reactive.function.client.ClientResponse
                .create(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).build())).build();
        var client=new SmartBootBudgetCostBusinessClient(http,new ObjectMapper(),"s");
        var first=new BudgetCostQueryInput(null,null,null,null,null,null,null,null,null,null);
        assertThatThrownBy(()->client.compare(new ToolContext("t","u","i",Set.of("p1")),new BudgetCostCompareInput("CUSTOM",first,first)))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).hasMessageContaining("缺少金额");
    }
    @Test void forbiddenResponseKeepsTheFinancialPermissionError() {
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(
                org.springframework.web.reactive.function.client.ClientResponse
                        .create(org.springframework.http.HttpStatus.FORBIDDEN).build())).build();
        var business = new SmartBootBudgetCostBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new BudgetCostQueryInput(null, null, null, "summary", null, null, null, 1, 20, null)))
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
                        "{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"registrationCount\":2,\"budgetAmount\":123456789012345.67}],\"columns\":[{\"key\":\"budgetAmount\",\"label\":\"预算成本\",\"type\":\"number\"}],\"appliedFilters\":[]}"))
                        .then();
            });
        }).bindNow();
        try {
            var client = new SmartBootBudgetCostBusinessClient(
                    WebClient.builder().baseUrl("http://127.0.0.1:" + server.port()).build(), new ObjectMapper(), "test-secret");
            var context = new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of("p2", "p1"), Set.of("menu:budget:cost"));
            var input = new BudgetCostQueryInput("p1", "测试项目", null, "summary", "projectCreateDate",
                    "2026-01-01", "2026-01-31", 1, 20, null);
            var result = client.query(context, input);
            assertThat(uri.get()).isEqualTo("/internal/ai/tools/budget-cost-query");
            assertThat(new ObjectMapper().readTree(body.get()).path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(new ObjectMapper().readTree(body.get()).path("dateField").asText()).isEqualTo("projectCreateDate");
            assertThat(result.rows().getFirst().get("budgetAmount").toString()).isEqualTo("123456789012345.67");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp.get() + "\nPOST\n/internal/ai/tools/budget-cost-query\ntenant\nuser\nidentity\nrole\nmenu:budget:cost\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } finally { server.disposeNow(); }
    }

    @Test void explicitlyUnauthorizedProjectNeverMakesAnHttpRequest() {
        WebClient client = WebClient.builder().exchangeFunction(request -> { throw new AssertionError("must not call"); }).build();
        var business = new SmartBootBudgetCostBusinessClient(client, new ObjectMapper(), "test-secret");
        assertThatThrownBy(() -> business.query(new ToolContext("tenant", "user", "identity", Set.of("p1")),
                new BudgetCostQueryInput("denied", null, null, "summary", null, null, null, 1, 20, null)))
                .isInstanceOf(SecurityException.class);
    }
}
