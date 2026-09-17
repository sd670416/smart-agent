package com.smart.agent.tool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelCredentialSource;
import com.smart.agent.model.ModelGatewayProperties;
import com.smart.agent.model.ModelRunContext;
import com.smart.agent.model.config.ModelCapability;
import com.smart.agent.tool.ToolContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * 与 OpenAI 提供方同一条契约：一轮运行内使用句柄持有的凭据，
     * 配置中途变化后也不回查数据库里的最新版本。
     */
    @Test
    void usesRunScopedCredentialsInsteadOfQueryingTheLatestStoredConfig() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger sourceQueries = new AtomicInteger();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            authorization.set(request.requestHeaders().get("Authorization"));
            return request.receive().aggregate().asString().flatMap(content -> {
                body.set(content);
                return response.header("Content-Type", "application/json")
                        .sendString(Mono.just("{\"choices\":[],\"web_search\":[]}")).then();
            });
        }).bindNow();
        try {
            String runBaseUrl = "http://127.0.0.1:" + server.port() + "/api/paas/v4";
            ModelGatewayProperties model = new ModelGatewayProperties("openai-compatible",
                    "http://127.0.0.1:1/api/paas/v4", "unused-key", "unused-model",
                    Duration.ofSeconds(1), Duration.ofSeconds(2));
            ModelCredentialSource latestStored = modelId -> {
                sourceQueries.incrementAndGet();
                return new ModelCredentialSource.Credentials(
                        "https://rotated.bigmodel.cn/api/paas/v4", "sk-rotated", "glm-4.6");
            };
            ZhipuWebSearchProvider provider = new ZhipuWebSearchProvider(
                    WebClient.builder().build(), MAPPER, model, latestStored, Duration.ofSeconds(2), CLOCK,
                    ZoneId.of("Asia/Shanghai"));

            providerSearchWithinRun(provider, runBaseUrl);

            assertThat(authorization.get()).isEqualTo("Bearer sk-pinned");
            assertThat(MAPPER.readTree(body.get()).path("model").asText()).isEqualTo("glm-4.5-pinned");
            assertThat(sourceQueries).hasValue(0);
        } finally {
            server.disposeNow();
        }
    }

    private static void providerSearchWithinRun(ZhipuWebSearchProvider provider, String runBaseUrl) {
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of(),
                Set.of("ai:web-search"))
                .withModelBinding(new ToolContext.ModelBinding("model-1", "展示名", "glm-4.5-pinned", runBaseUrl,
                        Set.of(ModelCapability.WEB_SEARCH)));
        ModelRunContext.with(
                new ModelCredentialSource.Credentials(runBaseUrl, "sk-pinned", "glm-4.5-pinned"),
                () -> provider.search(new WebSearchInput("天津天气", 5, null), context));
    }
}
