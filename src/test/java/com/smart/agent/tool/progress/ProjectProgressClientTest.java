package com.smart.agent.tool.progress;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
class ProjectProgressClientTest {
    @Test void specificProjectRestrictsRequestRatherThanQueryingAllVisibleProjects() throws Exception {
        AtomicReference<String> body=new AtomicReference<>();
        var http=WebClient.builder().exchangeFunction(r->r.body().insert(new org.springframework.mock.http.client.reactive.MockClientHttpRequest(HttpMethod.POST,r.url()) {
            @Override public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> data) {
                return org.springframework.core.io.buffer.DataBufferUtils.join(data).doOnNext(buffer->{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);body.set(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));org.springframework.core.io.buffer.DataBufferUtils.release(buffer);}).then();
            }
        },new org.springframework.web.reactive.function.BodyInserter.Context() {
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters(){return org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults().messageWriters();}
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest(){return Optional.empty();}
            public Map<String,Object> hints(){return Map.of();}
        }).thenReturn(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body("{\"mode\":\"detail\",\"rows\":[],\"columns\":[],\"appliedFilters\":[]}").build())).build();
        new SmartBootProjectProgressBusinessClient(http,new ObjectMapper(),"s").query(new ToolContext("t","u","i",Set.of("p","q")),new ProjectProgressQueryInput("p",null,"tasks",null,null,null,1,20));
        var json=new ObjectMapper().readTree(body.get());assertThat(json.path("projectIds").size()).isEqualTo(1);assertThat(json.path("projectIds").get(0).asText()).isEqualTo("p");
    }
    @Test void outsideProjectNeverSendsHttpRequest() {
        var client=new SmartBootProjectProgressBusinessClient(WebClient.builder().exchangeFunction(r->{throw new AssertionError("outside scope");}).build(),new ObjectMapper(),"s");
        assertThatThrownBy(()->client.query(new ToolContext("t","u","i",Set.of("p")),new ProjectProgressQueryInput("outside",null,"tasks",null,null,null,1,20))).isInstanceOf(SecurityException.class);
    }
    @Test void paginationIsValidatedBeforeRequest() {
        assertThatThrownBy(()->new ProjectProgressQueryInput(null,null,"detail",null,null,null,0,20)).isInstanceOf(IllegalArgumentException.class);
    }
}
