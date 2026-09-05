package com.smart.agent.authorization;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RoleKnowledgeGrantService {
    private final Map<String, Set<UUID>> grants = new ConcurrentHashMap<>();

    public Set<UUID> replaceGrants(String tenantId, String roleId, Set<UUID> spaceIds, AgentUserContext actor) {
        if (actor == null || !tenantId.equals(actor.tenantId()) || !actor.permissions().contains("ai:knowledge:manage"))
            throw new IllegalStateException("ai:knowledge:manage permission is required");
        Set<UUID> copy = Set.copyOf(spaceIds == null ? Set.of() : spaceIds);
        grants.put(key(tenantId, roleId), copy);
        return copy;
    }

    public Set<UUID> spacesForRoles(String tenantId, Set<String> roleIds) {
        return roleIds.stream().flatMap(r -> grants.getOrDefault(key(tenantId, r), Set.of()).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> grantsForRole(String tenantId, String roleId) {
        return Set.copyOf(grants.getOrDefault(key(tenantId, roleId), Set.of()));
    }

    private static String key(String tenant, String role) { return tenant + "\u0000" + role; }
}
