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

class OpenAiWebSearchProviderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-11T07:00:00Z"), ZoneId.of("UTC"));

    @Test
    void sendsSingleQueryAndConvertsResponseCitations() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            authorization.set(request.requestHeaders().get("Authorization"));
            return request.receive().aggregate().asString().flatMap(content -> {
                body.set(content);
                return response.header("Content-Type", "application/json").sendString(Mono.just("""
                        {"output":[{"type":"message","content":[{"type":"output_text",
                        "text":"天津今天晴，最高30摄氏度。","annotations":[
                        {"type":"url_citation","title":"天津天气","url":"https://weather.example/tianjin"}]}]}]}
                        """)).then();
            });
        }).bindNow();
        try {
            OpenAiWebSearchProvider provider = provider(server.port(), Duration.ofSeconds(2));

            WebSearchResult result = provider.search(new WebSearchInput("天津今日天气", 5, "day"));

            JsonNode request = MAPPER.readTree(body.get());
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(request.path("model").asText()).isEqualTo("gpt-5");
            assertThat(request.path("input").asText()).isEqualTo("天津今日天气");
            assertThat(request.path("tools").get(0).path("type").asText()).isEqualTo("web_search");
            assertThat(request.path("max_output_tokens").asInt()).isPositive();
            assertThat(body.get()).doesNotContain("conversation", "tenant", "userId", "Authorization");
            assertThat(result.query()).isEqualTo("天津今日天气");
            assertThat(result.searchedAt()).isEqualTo("2026-09-11T15:00:00+08:00");
            assertThat(result.summary()).isEqualTo("天津今天晴，最高30摄氏度。");
            assertThat(result.provider()).isEqualTo("openai");
            assertThat(result.sources()).containsExactly(new WebSearchSource(
                    "天津天气", "https://weather.example/tianjin", null, null));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void mapsRateLimitAndProviderFailuresToStableCodes() {
        assertStatusMapsToCode(429, "AGENT_WEB_SEARCH_RATE_LIMITED");
        assertStatusMapsToCode(500, "AGENT_WEB_SEARCH_FAILED");
    }

    @Test
    void mapsSlowProviderToTimeoutCode() {
        DisposableServer server = HttpServer.create().port(0).handle((request, response) ->
                response.header("Content-Type", "application/json")
                        .sendString(Mono.delay(Duration.ofMillis(250)).map(ignored -> "{\"output\":[]}")))
                .bindNow();
        try {
            assertThatThrownBy(() -> provider(server.port(), Duration.ofMillis(30))
                    .search(new WebSearchInput("天津天气", 5, null)))
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

    private OpenAiWebSearchProvider provider(int port, Duration timeout) {
        ModelGatewayProperties model = new ModelGatewayProperties(
                "openai-compatible", "http://127.0.0.1:" + port + "/v1", "test-key", "gpt-5",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        return new OpenAiWebSearchProvider(WebClient.builder().build(), MAPPER, model, timeout, CLOCK,
                ZoneId.of("Asia/Shanghai"));
    }

    private OpenAiWebSearchProvider provider(
            ModelGatewayProperties model, ModelCredentialSource credentials, Duration timeout) {
        return new OpenAiWebSearchProvider(WebClient.builder().build(), MAPPER, model, credentials, timeout, CLOCK,
                ZoneId.of("Asia/Shanghai"));
    }

    private static ToolContext bindingContext(String modelId, String baseUrl, String modelName) {
        return new ToolContext("tenant", "user", "identity", Set.of("role"), Set.of(), Set.of("ai:web-search"))
                .withModelBinding(new ToolContext.ModelBinding(modelId, "展示名", modelName, baseUrl,
                        Set.of(ModelCapability.WEB_SEARCH)));
    }

    /** 记录请求的本地桩服务，返回空的 OpenAI 响应。 */
    private static DisposableServer recordingServer(AtomicReference<String> authorization, AtomicReference<String> body) {
        return HttpServer.create().port(0).handle((request, response) -> {
            authorization.set(request.requestHeaders().get("Authorization"));
            return request.receive().aggregate().asString().flatMap(content -> {
                body.set(content);
                return response.header("Content-Type", "application/json")
                        .sendString(Mono.just("{\"output\":[]}")).then();
            });
        }).bindNow();
    }

    /**
     * P0 核心断言：一轮运行内联网必须使用句柄持有的凭据，
     * 即使数据库里的配置已经被改成新版本，也绝不能回查最新配置。
     */
    @Test
    void usesRunScopedCredentialsInsteadOfQueryingTheLatestStoredConfig() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger sourceQueries = new AtomicInteger();
        DisposableServer server = recordingServer(authorization, body);
        try {
            String runBaseUrl = "http://127.0.0.1:" + server.port() + "/v1";
            ModelGatewayProperties model = new ModelGatewayProperties("openai-compatible",
                    "http://127.0.0.1:1/v1", "unused-key", "unused-model",
                    Duration.ofSeconds(1), Duration.ofSeconds(2));
            ModelCredentialSource latestStored = modelId -> {
                sourceQueries.incrementAndGet();
                return new ModelCredentialSource.Credentials("https://rotated.example.com/v1", "sk-rotated", "gpt-5-new");
            };
            OpenAiWebSearchProvider provider = provider(model, latestStored, Duration.ofSeconds(2));

            WebSearchResult result = ModelRunContext.with(
                    new ModelCredentialSource.Credentials(runBaseUrl, "sk-pinned", "gpt-5-pinned"),
                    () -> provider.search(new WebSearchInput("天津天气", 5, null),
                            bindingContext("model-1", runBaseUrl, "gpt-5-pinned")));

            JsonNode request = MAPPER.readTree(body.get());
            assertThat(authorization.get()).isEqualTo("Bearer sk-pinned");
            assertThat(request.path("model").asText()).isEqualTo("gpt-5-pinned");
            assertThat(sourceQueries).hasValue(0);
            assertThat(result.provider()).isEqualTo("openai");
        } finally {
            server.disposeNow();
        }
    }

    /** 没有运行上下文时才允许按模型 ID 兜底换取（未接入运行链路的直接调用）。 */
    @Test
    void fallsBackToTheStoredCredentialsOnlyOutsideARun() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        DisposableServer server = recordingServer(authorization, body);
        try {
            String storedBaseUrl = "http://127.0.0.1:" + server.port() + "/v1";
            ModelGatewayProperties model = new ModelGatewayProperties("openai-compatible",
                    "http://127.0.0.1:1/v1", "unused-key", "unused-model",
                    Duration.ofSeconds(1), Duration.ofSeconds(2));
            ModelCredentialSource stored = modelId ->
                    new ModelCredentialSource.Credentials(storedBaseUrl, "sk-stored", "gpt-5-stored");
            OpenAiWebSearchProvider provider = provider(model, stored, Duration.ofSeconds(2));

            provider.search(new WebSearchInput("天津天气", 5, null),
                    bindingContext("model-1", storedBaseUrl, "gpt-5-stored"));

            assertThat(authorization.get()).isEqualTo("Bearer sk-stored");
            assertThat(MAPPER.readTree(body.get()).path("model").asText()).isEqualTo("gpt-5-stored");
        } finally {
            server.disposeNow();
        }
    }

    /** 运行期凭据没有密钥时必须直接给中文引导，且不得回退去取新版本的密钥。 */
    @Test
    void rejectsTheRunCredentialWithoutApiKeyInsteadOfUsingTheNewerOne() {
        AtomicInteger sourceQueries = new AtomicInteger();
        ModelGatewayProperties model = new ModelGatewayProperties("openai-compatible",
                "http://127.0.0.1:1/v1", "unused-key", "unused-model",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        ModelCredentialSource latestStored = modelId -> {
            sourceQueries.incrementAndGet();
            return new ModelCredentialSource.Credentials("https://rotated.example.com/v1", "sk-rotated", "gpt-5-new");
        };
        OpenAiWebSearchProvider provider = provider(model, latestStored, Duration.ofSeconds(2));

        assertThatThrownBy(() -> ModelRunContext.with(
                new ModelCredentialSource.Credentials("http://127.0.0.1:1/v1", null, "gpt-5"),
                () -> provider.search(new WebSearchInput("天津天气", 5, null),
                        bindingContext("model-1", "http://127.0.0.1:1/v1", "gpt-5"))))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED"));
        assertThat(sourceQueries).hasValue(0);
    }
}
