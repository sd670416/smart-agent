package com.smart.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LocalContextTokenVerifier implements ContextTokenVerifier {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Clock clock;

    @Autowired
    public LocalContextTokenVerifier(
            ObjectMapper objectMapper,
            @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) {
        this(objectMapper, secret, Clock.systemUTC());
    }

    LocalContextTokenVerifier(ObjectMapper objectMapper, String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("AGENT_LOCAL_CONTEXT_SECRET must not be blank");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.clock = clock;
    }

    @Override
    public AgentUserContext verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 2) {
            throw AgentException.unauthorized();
        }
        try {
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expectedSignature = mac.doFinal(parts[0].getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                throw AgentException.unauthorized();
            }
            TokenPayload payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), TokenPayload.class);
            if (payload.exp() <= Instant.now(clock).getEpochSecond()) {
                throw AgentException.unauthorized();
            }
            return new AgentUserContext(
                    payload.tenantId(),
                    payload.userId(),
                    payload.identityId(),
                    payload.permissions(),
                    payload.projectIds());
        } catch (AgentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AgentException.unauthorized();
        }
    }

    private record TokenPayload(
            String tenantId,
            String userId,
            String identityId,
            Set<String> permissions,
            Set<String> projectIds,
            long exp) {
    }
}
