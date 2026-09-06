package com.smart.agent.authorization;

import com.smart.agent.security.AgentUserContext;
import java.util.Set;

public interface ProjectPermissionClient {
    Set<String> accessibleProjects(AgentUserContext context, Set<String> requestedProjectIds);
}
