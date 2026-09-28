package com.smart.agent.clarification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PendingClarificationService {
    static final int MAX_JSON_BYTES = 128 * 1024;
    private static final Duration MAX_TTL = Duration.ofMinutes(30);

    private final PendingClarificationRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PendingClarificationService(PendingClarificationRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    PendingClarificationService(
            PendingClarificationRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PendingClarification create(String tenantId, String userId, String conversationId,
            String sourceDomain, String intentJson, String optionsJson, Duration ttl) {
        requireJsonSize(intentJson, "intentJson");
        requireJsonSize(optionsJson, "optionsJson");
        validateIntent(intentJson);
        validateOptions(optionsJson);
        Duration effectiveTtl = requireTtl(ttl);
        Instant now = clock.instant();
        repository.findActive(tenantId, userId, conversationId).ifPresent(previous -> {
            previous.cancel(now);
            repository.save(previous);
            repository.flush();
        });
        return repository.save(PendingClarification.create(tenantId, userId, conversationId,
                sourceDomain, intentJson, optionsJson, now.plus(effectiveTtl)));
    }

    @Transactional
    public Optional<PendingClarification> findActive(String tenantId, String userId, String conversationId) {
        Optional<PendingClarification> active = repository.findActive(tenantId, userId, conversationId);
        active.ifPresent(value -> {
            if (value.expireIfNecessary(clock.instant())) repository.save(value);
        });
        return active.filter(value -> value.status() == PendingClarification.Status.PENDING);
    }

    @Transactional(noRollbackFor = PendingClarificationException.class)
    public PendingClarification resolve(String tenantId, String userId, String conversationId,
            String clarificationId, String optionId) {
        PendingClarification value = repository.findByIdAndScope(
                        requireText(clarificationId, "clarificationId"), tenantId, userId, conversationId)
                .orElseThrow(this::notActive);
        Instant now = clock.instant();
        if (value.expireIfNecessary(now)) {
            repository.save(value);
            throw notActive();
        }
        value.requirePending(now);
        if (!containsOption(value.optionsJson(), optionId)) {
            throw new PendingClarificationException("待确认选项无效，请重新选择");
        }
        value.resolve(optionId, now);
        return repository.save(value);
    }

    @Transactional
    public void cancelActive(String tenantId, String userId, String conversationId) {
        Instant now = clock.instant();
        repository.findActive(tenantId, userId, conversationId).ifPresent(value -> {
            value.cancel(now);
            repository.save(value);
        });
    }

    private boolean containsOption(String optionsJson, String optionId) {
        String required = requireText(optionId, "optionId");
        try {
            for (JsonNode option : objectMapper.readTree(optionsJson)) {
                if (required.equals(option.path("id").asText())) return true;
            }
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored clarification options are invalid", exception);
        }
    }

    private void validateIntent(String value) {
        try {
            if (!objectMapper.readTree(value).isObject()) {
                throw new IllegalArgumentException("intentJson must be a JSON object");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("intentJson must be valid JSON", exception);
        }
    }

    private void validateOptions(String value) {
        try {
            JsonNode options = objectMapper.readTree(value);
            if (!options.isArray() || options.isEmpty() || options.size() > 10) {
                throw new IllegalArgumentException("optionsJson must contain 1 to 10 options");
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (JsonNode option : options) {
                String id = option.path("id").asText("").trim();
                String label = option.path("label").asText("").trim();
                if (id.isEmpty() || id.length() > 128 || label.isEmpty() || label.length() > 200 || !ids.add(id)) {
                    throw new IllegalArgumentException("clarification options must have unique id and label");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("optionsJson must be valid JSON", exception);
        }
    }

    private Duration requireTtl(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("clarification ttl must be between 1 millisecond and 30 minutes");
        }
        return value;
    }

    private void requireJsonSize(String value, String field) {
        requireText(value, field);
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException(field + " is too large");
        }
    }

    private PendingClarificationException notActive() {
        return new PendingClarificationException("待确认问题不存在或已失效，请重新提问");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
