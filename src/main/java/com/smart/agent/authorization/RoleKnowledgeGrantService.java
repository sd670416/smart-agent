package com.smart.agent.authorization;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleKnowledgeGrantService {
    private final Map<String, Set<UUID>> grants = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    public RoleKnowledgeGrantService() { this.jdbc = null; }
    public RoleKnowledgeGrantService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Set<UUID> replaceGrants(String tenantId, String roleId, Set<UUID> spaceIds, AgentUserContext actor) {
        if (actor == null || !tenantId.equals(actor.tenantId()) || !actor.permissions().contains("ai:knowledge:manage"))
            throw new IllegalStateException("ai:knowledge:manage permission is required");
        Set<UUID> copy = Set.copyOf(spaceIds == null ? Set.of() : spaceIds);
        if (jdbc != null) {
            for (UUID id : copy) {
                Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ai_knowledge_space WHERE id=? AND tenant_id=? AND deleted=b'0'", Integer.class, id.toString(), tenantId);
                if (n == null || n == 0) throw new IllegalArgumentException("knowledge space does not belong to tenant");
            }
            jdbc.update("DELETE FROM ai_role_knowledge_grant WHERE tenant_id=? AND role_id=?", tenantId, roleId);
            for (UUID id : copy) jdbc.update("INSERT INTO ai_role_knowledge_grant (id,tenant_id,role_id,knowledge_space_id,created_by,create_time) VALUES (UUID(),?,?,?,?,CURRENT_TIMESTAMP)", tenantId, roleId, id.toString(), actor.userId());
        } else grants.put(key(tenantId, roleId), copy);
        return copy;
    }

    public Set<UUID> spacesForRoles(String tenantId, Set<String> roleIds) {
        if (jdbc != null) {
            if (roleIds.isEmpty()) return Set.of();
            String q = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            List<Object> args = new ArrayList<>(); args.add(tenantId); args.addAll(roleIds);
            return jdbc.query("SELECT DISTINCT knowledge_space_id FROM ai_role_knowledge_grant WHERE tenant_id=? AND role_id IN ("+q+")", (rs,n)->UUID.fromString(rs.getString(1)), args.toArray()).stream().collect(Collectors.toUnmodifiableSet());
        }
        return roleIds.stream().flatMap(r -> grants.getOrDefault(key(tenantId, r), Set.of()).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> grantsForRole(String tenantId, String roleId) {
        return Set.copyOf(grants.getOrDefault(key(tenantId, roleId), Set.of()));
    }

    private static String key(String tenant, String role) { return tenant + "\u0000" + role; }
}
