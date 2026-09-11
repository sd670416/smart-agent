package com.smart.agent.tool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelGatewayProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WebSearchProviderRouterTest {
    @Test
    void autoRoutesGptModelToOpenAi() {
        Calls calls = new Calls();
        WebSearchProviderRouter router = router("auto", "https://api.openai.com/v1", "gpt-5", calls);

        WebSearchResult result = router.search(new WebSearchInput("公开新闻", 5, null));

        assertThat(result.provider()).isEqualTo("openai");
        assertThat(calls.openAi.get()).isEqualTo(1);
        assertThat(calls.zhipu.get()).isZero();
    }

    @Test
    void autoRoutesGlmModelToZhipu() {
        Calls calls = new Calls();
        WebSearchProviderRouter router = router("auto", "https://open.bigmodel.cn/api/paas/v4", "glm-4.5", calls);

        WebSearchResult result = router.search(new WebSearchInput("公开政策", 5, null));

        assertThat(result.provider()).isEqualTo("zhipu");
        assertThat(calls.zhipu.get()).isEqualTo(1);
        assertThat(calls.openAi.get()).isZero();
    }

    @Test
    void explicitProviderOverridesAmbiguousCompatibleEndpoint() {
        Calls calls = new Calls();
        WebSearchProviderRouter router = router("zhipu", "https://gateway.example/v1", "custom-model", calls);

        assertThat(router.search(new WebSearchInput("天气", 5, null)).provider()).isEqualTo("zhipu");
    }

    @Test
    void rejectsUnknownProviderWithoutTryingEitherService() {
        Calls calls = new Calls();
        WebSearchProviderRouter router = router("auto", "https://gateway.example/v1", "custom-model", calls);

        assertThatThrownBy(() -> router.search(new WebSearchInput("天气", 5, null)))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED"));
        assertThat(calls.openAi.get()).isZero();
        assertThat(calls.zhipu.get()).isZero();
    }

    private WebSearchProviderRouter router(
            String provider, String baseUrl, String modelName, Calls calls) {
        WebSearchProperties web = new WebSearchProperties(true, provider, 5, Duration.ofSeconds(15));
        ModelGatewayProperties model = new ModelGatewayProperties(
                "openai-compatible", baseUrl, "key", modelName,
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        WebSearchProvider openAi = input -> {
            calls.openAi.incrementAndGet();
            return new WebSearchResult(input.query(), "now", "openai", List.of(), "openai");
        };
        WebSearchProvider zhipu = input -> {
            calls.zhipu.incrementAndGet();
            return new WebSearchResult(input.query(), "now", "zhipu", List.of(), "zhipu");
        };
        return new WebSearchProviderRouter(web, model, openAi, zhipu);
    }

    private static final class Calls {
        private final AtomicInteger openAi = new AtomicInteger();
        private final AtomicInteger zhipu = new AtomicInteger();
    }
}
