package com.smart.agent.tool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelGatewayProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class ZhipuWebSearchProviderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-11T07:00:00Z"), ZoneId.of("UTC"));

    @Test
    void sendsNativeSearchRequestAndConvertsSources() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            authorization.set(request.requestHeaders().get("Authorization"));
            return request.receive().aggregate().asString().flatMap(content -> {
                body.set(content);
                return response.header("Content-Type", "application/json").sendString(Mono.just("""
                        {"choices":[{"message":{"role":"assistant","content":"天津今天晴。"}}],
                        "web_search":[{"title":"天津天气","link":"https://weather.example/tianjin",
                        "content":"今日晴朗","publish_date":"2026-09-11"}]}
                        """)).then();
            });
        }).bindNow();
        try {
            ZhipuWebSearchProvider provider = provider(server.port(), Duration.ofSeconds(2));

            WebSearchResult result = provider.search(new WebSearchInput("天津今日天气", 5, "day"));

            JsonNode request = MAPPER.readTree(body.get());
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(request.path("model").asText()).isEqualTo("glm-4.5");
            assertThat(request.path("messages").size()).isEqualTo(1);
            assertThat(request.path("messages").get(0).path("content").asText()).isEqualTo("天津今日天气");
            assertThat(request.path("tools").get(0).path("type").asText()).isEqualTo("web_search");
            assertThat(request.path("tools").get(0).path("web_search").path("search_query").asText())
                    .isEqualTo("天津今日天气");
            assertThat(body.get()).doesNotContain("conversation", "tenant", "userId", "Authorization");
            assertThat(result.summary()).isEqualTo("天津今天晴。");
            assertThat(result.provider()).isEqualTo("zhipu");
            assertThat(result.sources()).containsExactly(new WebSearchSource(
                    "天津天气", "https://weather.example/tianjin", "今日晴朗", "2026-09-11"));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void mapsRateLimitTimeoutAndProviderFailureToStableCodes() {
        assertStatusMapsToCode(429, "AGENT_WEB_SEARCH_RATE_LIMITED");
        assertStatusMapsToCode(500, "AGENT_WEB_SEARCH_FAILED");

        DisposableServer server = HttpServer.create().port(0).handle((request, response) ->
                response.sendString(Mono.delay(Duration.ofMillis(250)).map(ignored -> "{\"choices\":[]}")))
                .bindNow();
        try {
            assertThatThrownBy(() -> provider(server.port(), Duration.ofMillis(30))
                    .search(new WebSearchInput("天气", 5, null)))
                    .isInstanceOf(AgentException.class)
                    .satisfies(error -> assertThat(((AgentException) error).code())
                            .isEqualTo("AGENT_WEB_SEARCH_TIMEOUT"));
        } finally {
            server.disposeNow();
        }
    }

    private void assertStatusMapsToCode(int status, String code) {
        DisposableServer server = HttpServer.create().port(0).handle((request, response) ->
                response.status(status).send()).bindNow();
        try {
            assertThatThrownBy(() -> provider(server.port(), Duration.ofSeconds(2))
                    .search(new WebSearchInput("公开新闻", 5, null)))
                    .isInstanceOf(AgentException.class)
                    .satisfies(error -> assertThat(((AgentException) error).code()).isEqualTo(code));
        } finally {
            server.disposeNow();
        }
    }

    private ZhipuWebSearchProvider provider(int port, Duration timeout) {
        ModelGatewayProperties model = new ModelGatewayProperties(
                "openai-compatible", "http://127.0.0.1:" + port + "/api/paas/v4", "test-key", "glm-4.5",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        return new ZhipuWebSearchProvider(WebClient.builder().build(), MAPPER, model, timeout, CLOCK,
                ZoneId.of("Asia/Shanghai"));
    }
}
