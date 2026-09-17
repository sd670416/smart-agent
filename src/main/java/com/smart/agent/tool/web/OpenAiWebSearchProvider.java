package com.smart.agent.tool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelCredentialSource;
import com.smart.agent.model.ModelGatewayProperties;
import com.smart.agent.model.ModelRunContext;
import com.smart.agent.tool.ToolContext;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class OpenAiWebSearchProvider implements WebSearchProvider {
    private static final DateTimeFormatter SEARCHED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final ModelGatewayProperties model;
    private final ModelCredentialSource credentials;
    private final Duration timeout;
    private final Clock clock;
    private final ZoneId timezone;

    public OpenAiWebSearchProvider(
            WebClient client,
            ObjectMapper objectMapper,
            ModelGatewayProperties model,
            ModelCredentialSource credentials,
            Duration timeout,
            Clock clock,
            ZoneId timezone) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
        this.credentials = credentials == null ? ModelCredentialSource.empty() : credentials;
        this.timeout = timeout;
        this.clock = clock;
        this.timezone = timezone;
    }

    public OpenAiWebSearchProvider(
            WebClient client,
            ObjectMapper objectMapper,
            ModelGatewayProperties model,
            Duration timeout,
            Clock clock,
            ZoneId timezone) {
        this(client, objectMapper, model, ModelCredentialSource.empty(), timeout, clock, timezone);
    }

    /** 无上下文调用：按全局配置执行，保留给未接入模型配置中心的场景。 */
    @Override
    public WebSearchResult search(WebSearchInput input) {
        return search(input, null);
    }

    @Override
    public WebSearchResult search(WebSearchInput input, ToolContext context) {
        ResolvedModel resolved = resolve(context);
        try {
            JsonNode response = client.post()
                    .uri(endpoint(resolved.baseUrl()))
                    .headers(headers -> headers.setBearerAuth(resolved.apiKey()))
                    .bodyValue(requestBody(input, resolved.modelName()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);
            if (response == null) return failure("OpenAI web search returned an empty response");
            return convert(input, response);
        } catch (WebClientResponseException.TooManyRequests error) {
            throw new AgentException("AGENT_WEB_SEARCH_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                    "OpenAI web search rate limit exceeded");
        } catch (WebClientResponseException error) {
            return failure("OpenAI web search request failed with status " + error.getStatusCode().value());
        } catch (AgentException error) {
            throw error;
        } catch (RuntimeException error) {
            if (containsTimeout(error)) {
                throw new AgentException("AGENT_WEB_SEARCH_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                        "OpenAI web search timed out");
            }
            return failure("OpenAI web search request failed");
        }
    }

    /**
     * 解析本次请求使用的连接信息。
     *
     * <p>优先读取本轮运行的私有上下文：它与聊天使用同一份配置版本，
     * 因此一轮对话中途改配置不会让联网用上新版本地址或密钥。
     * 只有在没有运行上下文时（直接调用本提供方、未接入模型配置中心的部署）
     * 才按模型 ID 兜底换取——那种场景不存在与运行快照的版本差异。
     */
    private ResolvedModel resolve(ToolContext context) {
        ToolContext.ModelBinding binding = context == null ? null : context.modelBinding();
        if (binding == null) {
            return new ResolvedModel(model.baseUrl(), model.apiKey(), model.chatModel());
        }
        ModelCredentialSource.Credentials resolved = ModelRunContext.credentials();
        if (resolved == null) {
            resolved = this.credentials.credentialsFor(binding.modelId());
        }
        if (resolved == null || !resolved.hasApiKey()) {
            throw new AgentException("AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED", HttpStatus.BAD_REQUEST,
                    "Selected model has no usable credentials for web search");
        }
        return new ResolvedModel(
                resolved.baseUrl() == null || resolved.baseUrl().isBlank() ? model.baseUrl() : resolved.baseUrl(),
                resolved.apiKey(),
                resolved.modelName() == null || resolved.modelName().isBlank()
                        ? binding.modelName() : resolved.modelName());
    }

    private ObjectNode requestBody(WebSearchInput input, String modelName) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", modelName);
        request.put("input", input.query());
        request.put("max_output_tokens", Math.max(512, input.maxResults() * 256));
        ArrayNode tools = request.putArray("tools");
        tools.addObject().put("type", "web_search");
        return request;
    }

    /** 一次请求所用的连接信息，仅在本次调用内存在。 */
    private record ResolvedModel(String baseUrl, String apiKey, String modelName) {
    }

    private WebSearchResult convert(WebSearchInput input, JsonNode response) {
        List<String> textParts = new ArrayList<>();
        List<WebSearchSource> sources = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) continue;
            for (JsonNode content : output.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) continue;
                String text = content.path("text").asText("").trim();
                if (!text.isEmpty()) textParts.add(text);
                for (JsonNode annotation : content.path("annotations")) {
                    addCitation(annotation, sources, seenUrls, input.maxResults());
                }
            }
        }
        return new WebSearchResult(
                input.query(),
                ZonedDateTime.now(clock).withZoneSameInstant(timezone).format(SEARCHED_AT),
                String.join("\n", textParts),
                sources,
                "openai");
    }

    private void addCitation(
            JsonNode annotation, List<WebSearchSource> sources, Set<String> seenUrls, int maxResults) {
        if (sources.size() >= maxResults || !"url_citation".equals(annotation.path("type").asText())) return;
        String url = annotation.path("url").asText("");
        if (!isSafeHttpsUrl(url) || !seenUrls.add(url)) return;
        String title = nullableText(annotation, "title");
        sources.add(new WebSearchSource(title == null ? url : title, url,
                nullableText(annotation, "snippet"), nullableText(annotation, "published_at")));
    }

    private static boolean isSafeHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.textValue().isBlank() ? null : value.textValue();
    }

    private static String endpoint(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/responses";
    }

    private static boolean containsTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) return true;
        }
        return error.getMessage() != null && error.getMessage().toLowerCase().contains("timeout");
    }

    private static <T> T failure(String message) {
        throw new AgentException("AGENT_WEB_SEARCH_FAILED", HttpStatus.BAD_GATEWAY, message);
    }
}
