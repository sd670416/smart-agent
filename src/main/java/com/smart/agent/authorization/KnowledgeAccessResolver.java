package com.smart.agent.authorization;

import com.smart.agent.knowledge.manage.*;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@Service
@ConditionalOnBean({ProjectPermissionClient.class, KnowledgeAccessResolver.SpaceReader.class})
public class KnowledgeAccessResolver {
    public record AuthorizedKnowledgeSpace(UUID id, KnowledgeScope scope, String projectId) {}
    private final RoleKnowledgeGrantService grants;
    private final SpaceReader spaces;
    private final ProjectPermissionClient projects;

    public interface SpaceReader { Optional<KnowledgeSpace> find(UUID id, String tenantId); }

    public KnowledgeAccessResolver(RoleKnowledgeGrantService grants, SpaceReader spaces, ProjectPermissionClient projects) {
        this.grants = Objects.requireNonNull(grants); this.spaces = Objects.requireNonNull(spaces); this.projects = Objects.requireNonNull(projects);
    }

    public Set<AuthorizedKnowledgeSpace> resolve(AgentUserContext context) {
        Set<UUID> ids = grants.spacesForRoles(context.tenantId(), context.roleIds());
        Set<String> allowedProjects = projects.accessibleProjects(context, context.projectIds());
        return ids.stream().map(id -> spaces.find(id, context.tenantId()).orElse(null)).filter(Objects::nonNull)
                .filter(s -> s.status() == KnowledgeStatus.PUBLISHED)
                .filter(s -> s.scope() == KnowledgeScope.TENANT || (s.projectId() != null && allowedProjects.contains(s.projectId())))
                .map(s -> new AuthorizedKnowledgeSpace(s.id(), s.scope(), s.projectId())).collect(Collectors.toUnmodifiableSet());
    }
}
