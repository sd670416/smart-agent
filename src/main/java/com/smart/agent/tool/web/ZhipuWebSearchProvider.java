package com.smart.agent.tool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelGatewayProperties;
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

public final class ZhipuWebSearchProvider implements WebSearchProvider {
    private static final DateTimeFormatter SEARCHED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final ModelGatewayProperties model;
    private final Duration timeout;
    private final Clock clock;
    private final ZoneId timezone;

    public ZhipuWebSearchProvider(
            WebClient client,
            ObjectMapper objectMapper,
            ModelGatewayProperties model,
            Duration timeout,
            Clock clock,
            ZoneId timezone) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
        this.timeout = timeout;
        this.clock = clock;
        this.timezone = timezone;
    }

    @Override
    public WebSearchResult search(WebSearchInput input) {
        try {
            JsonNode response = client.post()
                    .uri(endpoint(model.baseUrl()))
                    .headers(headers -> headers.setBearerAuth(model.apiKey()))
                    .bodyValue(requestBody(input))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);
            if (response == null) return failure("Zhipu web search returned an empty response");
            return convert(input, response);
        } catch (WebClientResponseException.TooManyRequests error) {
            throw new AgentException("AGENT_WEB_SEARCH_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                    "Zhipu web search rate limit exceeded");
        } catch (WebClientResponseException error) {
            return failure("Zhipu web search request failed with status " + error.getStatusCode().value());
        } catch (AgentException error) {
            throw error;
        } catch (RuntimeException error) {
            if (containsTimeout(error)) {
                throw new AgentException("AGENT_WEB_SEARCH_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
                        "Zhipu web search timed out");
            }
            return failure("Zhipu web search request failed");
        }
    }

    private ObjectNode requestBody(WebSearchInput input) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model.chatModel());
        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "user").put("content", input.query());
        ObjectNode webSearch = request.putArray("tools").addObject()
                .put("type", "web_search").putObject("web_search");
        webSearch.put("enable", true);
        webSearch.put("search_query", input.query());
        request.put("stream", false);
        return request;
    }

    private WebSearchResult convert(WebSearchInput input, JsonNode response) {
        String summary = "";
        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            summary = choices.get(0).path("message").path("content").asText("").trim();
        }
        List<WebSearchSource> sources = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (JsonNode source : response.path("web_search")) {
            if (sources.size() >= input.maxResults()) break;
            String url = source.path("link").asText(source.path("url").asText(""));
            if (!isSafeHttpsUrl(url) || !seenUrls.add(url)) continue;
            String title = nullableText(source, "title");
            sources.add(new WebSearchSource(
                    title == null ? url : title,
                    url,
                    firstText(source, "content", "snippet"),
                    firstText(source, "publish_date", "published_at")));
        }
        return new WebSearchResult(
                input.query(),
                ZonedDateTime.now(clock).withZoneSameInstant(timezone).format(SEARCHED_AT),
                summary,
                sources,
                "zhipu");
    }

    private static boolean isSafeHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String firstText(JsonNode node, String first, String second) {
        String value = nullableText(node, first);
        return value == null ? nullableText(node, second) : value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.textValue().isBlank() ? null : value.textValue();
    }

    private static String endpoint(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
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
