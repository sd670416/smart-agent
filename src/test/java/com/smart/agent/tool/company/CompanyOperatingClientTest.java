package com.smart.agent.tool.company;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.server.HttpServer;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
class CompanyOperatingClientTest {
    private CompanyOperatingQueryInput input(String projectId){return new CompanyOperatingQueryInput(projectId,null,null,"company",null,"projects","summary",null,null,null,1,20);}
    @Test void signedRequestPreservesIdentityScopeAndDecimalPrecision() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(),signature=new AtomicReference<>(),timestamp=new AtomicReference<>();
        var server=HttpServer.create().port(0).handle((request,response)->{
            assertThat(request.uri()).isEqualTo("/internal/ai/tools/company-operating-query");
            signature.set(request.requestHeaders().get("X-Agent-Internal-Signature"));timestamp.set(request.requestHeaders().get("X-Agent-Internal-Timestamp"));
            return request.receive().aggregate().asString().flatMap(content->{body.set(content);return response.header("Content-Type","application/json").sendString(Mono.just(
                    "{\"mode\":\"aggregate\",\"total\":1,\"rows\":[{\"contractAmount\":123456789012345.67}],\"columns\":[],\"appliedFilters\":[]}")).then();});
        }).bindNow();
        try {
            var client=new SmartBootCompanyOperatingBusinessClient(WebClient.builder().baseUrl("http://127.0.0.1:"+server.port()).build(),new ObjectMapper(),"s");
            var result=client.query(new ToolContext("t","u","i",Set.of("role"),Set.of("p1","p2"),Set.of("menu:project")),input("p1"));
            var json=new ObjectMapper().readTree(body.get());
            assertThat(json.path("companyId").asText()).isEqualTo("company");assertThat(json.path("metric").asText()).isEqualTo("projects");
            assertThat(json.path("projectIds").size()).isEqualTo(1);assertThat(json.path("projectIds").get(0).asText()).isEqualTo("p1");
            assertThat(result.rows().getFirst().get("contractAmount").toString()).isEqualTo("123456789012345.67");
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("s".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            assertThat(signature.get()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((timestamp.get()+"\nPOST\n/internal/ai/tools/company-operating-query\nt\nu\ni\nrole\nmenu:project\np1,p2").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } finally {server.disposeNow();}
    }
    @Test void outOfScopeProjectNeverSendsRequest() {
        var client=new SmartBootCompanyOperatingBusinessClient(WebClient.builder().exchangeFunction(r->{throw new AssertionError();}).build(),new ObjectMapper(),"s");
        assertThatThrownBy(()->client.query(new ToolContext("t","u","i",Set.of("p")),input("outside"))).isInstanceOf(SecurityException.class);
    }
    @Test void missingAmountIsNotMisreportedAsBadFilter() {
        var http=WebClient.builder().exchangeFunction(r->Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).build())).build();
        assertThatThrownBy(()->new SmartBootCompanyOperatingBusinessClient(http,new ObjectMapper(),"s").query(new ToolContext("t","u","i",Set.of("p")),input(null))).hasMessageContaining("缺少金额");
    }
}
