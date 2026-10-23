package com.smart.agent.tool.cost;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.server.HttpServer;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
class CostContractClientTest {
    @Test void comparisonUsesItsOwnSignedPathAndSeparateProjectScopes() throws Exception {
        AtomicReference<String> path=new AtomicReference<>(),body=new AtomicReference<>(),signature=new AtomicReference<>(),timestamp=new AtomicReference<>();
        var server=HttpServer.create().port(0).handle((request,response)->{
            path.set(request.uri());signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));
            timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            return request.receive().aggregate().asString().flatMap(content->{body.set(content);return response.header("Content-Type","application/json")
                    .sendString(Mono.just("{\"mode\":\"aggregate\",\"total\":5,\"rows\":[{\"currentAmount\":123456789012345.67}],\"columns\":[],\"appliedFilters\":[]}")).then();});
        }).bindNow();
        try {
            var client=new SmartBootCostContractBusinessClient(WebClient.builder().baseUrl("http://127.0.0.1:"+server.port()).build(),new ObjectMapper(),"test-secret");
            var context=new ToolContext("t","u","i",Set.of("role"),Set.of("p1","p2"),Set.of("menu:cost-contract:labor"));
            var first=new CostContractQueryInput("p1",null,null,null,null,null,"钢",null,null,null,null,null);
            var second=first.withProjectId("p2");
            var result=client.compare(context,new CostContractCompareInput("CUSTOM",first,second));
            assertThat(path.get()).isEqualTo("/internal/ai/tools/cost-contract-compare");
            var json=new ObjectMapper().readTree(body.get());
            assertThat(json.path("current").path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(json.path("baseline").path("projectIds").get(0).asText()).isEqualTo("p2");
            assertThat(json.path("baseline").path("contractName").asText()).isEqualTo("钢");
            assertThat(result.rows().getFirst().get("currentAmount").toString()).isEqualTo("123456789012345.67");
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            String canonical=timestamp.get()+"\nPOST\n/internal/ai/tools/cost-contract-compare\nt\nu\ni\nrole\nmenu:cost-contract:labor\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } finally {server.disposeNow();}
    }
    @Test void unauthorizedSecondGroupNeverSendsComparisonRequest() {
        var client=new SmartBootCostContractBusinessClient(WebClient.builder().exchangeFunction(r->{throw new AssertionError("must not send");}).build(),new ObjectMapper(),"s");
        var first=new CostContractQueryInput("p",null,null,null,null,null,null,null,null,null,null,null);
        assertThatThrownBy(()->client.compare(new ToolContext("t","u","i",Set.of("p")),
                new CostContractCompareInput("CUSTOM",first,first.withProjectId("outside")))).isInstanceOf(SecurityException.class);
    }
    @Test void incompleteApprovedAmountsExplainDataIssueRatherThanInvalidFilters() {
        var http=WebClient.builder().exchangeFunction(request->Mono.just(org.springframework.web.reactive.function.client.ClientResponse
                .create(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).build())).build();
        var client=new SmartBootCostContractBusinessClient(http,new ObjectMapper(),"s");
        assertThatThrownBy(()->client.query(new ToolContext("t","u","i",Set.of("p")),
                new CostContractQueryInput(null,null,null,"labor","summary",null,null,null,null,null,1,20)))
                .hasMessageContaining("缺少金额");
    }
    @Test void signedRequestCarriesSourceScopedProjectsAndPreciseAmounts() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(),signature=new AtomicReference<>(),timestamp=new AtomicReference<>();
        var server=HttpServer.create().port(0).handle((request,response)->{
            assertThat(request.uri()).isEqualTo("/internal/ai/tools/cost-contract-query");
            signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            return request.receive().aggregate().asString().flatMap(content->{body.set(content);return response.header("Content-Type","application/json")
                    .sendString(Mono.just("{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"contractAmount\":123456789012345.67}],\"columns\":[],\"appliedFilters\":[]}")).then();});
        }).bindNow();
        try {
            var client=new SmartBootCostContractBusinessClient(WebClient.builder().baseUrl("http://127.0.0.1:"+server.port()).build(),new ObjectMapper(),"test-secret");
            var context=new ToolContext("t","u","i",Set.of("role"),Set.of("p1","p2"),Set.of("menu:cost-contract:labor"));
            var result=client.query(context,new CostContractQueryInput("p1",null,null,"labor","summary",null,null,null,null,null,1,20));
            var json=new ObjectMapper().readTree(body.get());
            assertThat(json.path("source").asText()).isEqualTo("labor");assertThat(json.path("projectIds").size()).isEqualTo(1);
            assertThat(json.path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(result.rows().getFirst().get("contractAmount").toString()).isEqualTo("123456789012345.67");
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            String canonical=timestamp.get()+"\nPOST\n/internal/ai/tools/cost-contract-query\nt\nu\ni\nrole\nmenu:cost-contract:labor\np1,p2";
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } finally {server.disposeNow();}
    }
    @Test void untrustedProjectCannotSendHttpRequest() {
        var client=new SmartBootCostContractBusinessClient(WebClient.builder().exchangeFunction(r->{throw new AssertionError("must not send");}).build(),new ObjectMapper(),"s");
        assertThatThrownBy(()->client.query(new ToolContext("t","u","i",Set.of("p")),new CostContractQueryInput("outside",null,null,"labor","summary",null,null,null,null,null,1,20))).isInstanceOf(SecurityException.class);
    }
}
