package com.smart.agent.model.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class JdkModelTestTransport implements ModelTestTransport {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    /**
     * 发起一次模型连通性测试请求。
     *
     * <p>deploymentType 保留在签名中仅用于维持传输层契约，不再参与地址限制（见下方说明）。</p>
     */
    @Override
    public Response post(String url, ModelDeploymentType deploymentType, String apiKey, String jsonBody,
            Duration connectTimeout, Duration readTimeout) {
        long started = System.nanoTime();
        try {
            URI target = parseTarget(url);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            org.springframework.web.reactive.function.client.WebClient webClient =
                    org.springframework.web.reactive.function.client.WebClient.builder()
                            .clientConnector(new org.springframework.http.client.reactive.JdkClientHttpConnector(client))
                            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                            .build();
            Response response = webClient.post().uri(target)
                    .header("Content-Type", "application/json")
                    .headers(headers -> {
                        if (apiKey != null && !apiKey.isBlank()) headers.setBearerAuth(apiKey);
                    })
                    .bodyValue(jsonBody)
                    .exchangeToMono(result -> result.bodyToMono(String.class).defaultIfEmpty("")
                            .map(body -> new Response(result.statusCode().value(), body, elapsed(started))))
                    .timeout(readTimeout)
                    .onErrorMap(java.util.concurrent.TimeoutException.class,
                            failure -> new java.net.http.HttpTimeoutException("model test timeout"))
                    .block();
            if (response == null) throw new IllegalStateException("模型响应为空");
            return response;
        } catch (TargetRejectedException failure) {
            throw new ModelTestTransportException(failure.userMessage(), failure, elapsed(started));
        } catch (Exception failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new ModelTestTransportException("模型测试请求已取消", failure, elapsed(started));
                }
                if (cause instanceof org.springframework.core.io.buffer.DataBufferLimitException) {
                    throw new ModelTestTransportException(
                            "模型响应内容超过 1MB 上限，请确认服务地址指向 OpenAI 兼容的 Chat Completions 接口",
                            failure, elapsed(started));
                }
                if (cause instanceof java.net.http.HttpTimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException) {
                    throw new ModelTestTransportException("连接模型服务超时，请检查地址或调整超时时间", failure, elapsed(started));
                }
            }
            throw new ModelTestTransportException("无法连接模型服务，请检查服务地址和网络", failure, elapsed(started));
        }
    }

    /** 解析地址并给出可读中文提示；解析失败不能用泛化的网络错误掩盖。 */
    private static URI parseTarget(String url) {
        URI target;
        try {
            target = URI.create(url);
        } catch (RuntimeException failure) {
            throw new TargetRejectedException("模型服务地址格式不正确，请填写完整地址，例如 https://api.example.com/v1");
        }
        String host = target.getHost();
        if (host == null || host.isBlank()) {
            throw new TargetRejectedException("模型服务地址缺少主机名，请填写完整地址，例如 https://api.example.com/v1");
        }
        return target;
    }

    /*
     * 这里刻意不按 deploymentType 限制内网地址。
     *
     * 运行时聊天链路（OpenAiCompatibleModelGateway）本来就没有该限制，测试链路单独设限会造成
     * 「聊天能用、点测试报错」的错配。部署方式只是配置语义，不作为安全边界；测试入口的收敛
     * 由网关的 aiModel:test 鉴权与保存期地址校验共同保证。
     */

    /**
     * 地址在发起请求前就被拒绝的专用异常。
     *
     * <p>必须与真实的连接失败区分开，否则会被兜底分支吞成「无法连接模型服务」，
     * 用户看不到真实原因。</p>
     */
    private static final class TargetRejectedException extends RuntimeException {
        private final String userMessage;

        TargetRejectedException(String userMessage) {
            super(userMessage);
            this.userMessage = userMessage;
        }

        String userMessage() {
            return userMessage;
        }
    }

    private static long elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    static final class ModelTestTransportException extends RuntimeException {
        private final long durationMillis;

        ModelTestTransportException(String message, Throwable cause, long durationMillis) {
            super(message, cause);
            this.durationMillis = durationMillis;
        }

        long durationMillis() {
            return durationMillis;
        }
    }
}
