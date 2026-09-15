package com.smart.agent.model.config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class JdkModelTestTransport implements ModelTestTransport {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    @Override
    public Response post(String url, ModelDeploymentType deploymentType, String apiKey, String jsonBody,
            Duration connectTimeout, Duration readTimeout) {
        long started = System.nanoTime();
        try {
            URI target = URI.create(url);
            validateTarget(target, deploymentType);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest.Builder request = HttpRequest.newBuilder(target)
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream input = response.body()) {
                body = new String(readLimited(input), java.nio.charset.StandardCharsets.UTF_8);
            }
            return new Response(response.statusCode(), body, elapsed(started));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ModelTestTransportException("模型测试请求已取消", failure, elapsed(started));
        } catch (java.net.http.HttpTimeoutException failure) {
            throw new ModelTestTransportException("连接模型服务超时，请检查地址或调整超时时间", failure, elapsed(started));
        } catch (Exception failure) {
            throw new ModelTestTransportException("无法连接模型服务，请检查服务地址和网络", failure, elapsed(started));
        }
    }

    private static void validateTarget(URI target, ModelDeploymentType deploymentType) throws Exception {
        if (deploymentType != ModelDeploymentType.CLOUD) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(target.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("云端模型地址不能指向本机或内部网络");
            }
        }
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("模型响应超过 1MB 限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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
