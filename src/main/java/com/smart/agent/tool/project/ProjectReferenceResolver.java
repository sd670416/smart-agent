package com.smart.agent.tool.project;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves identifiers against current permission-filtered projects, never against chat text alone. */
@Component
public class ProjectReferenceResolver {
    public record Reference(String projectId, String projectCode, String projectName) {}

    private final ProjectBusinessClient projects;

    public ProjectReferenceResolver(ProjectBusinessClient projects) { this.projects = projects; }

    public List<Reference> resolve(ToolContext context, String projectId, String projectCode, String projectName) {
        if (context.projectIds().isEmpty()) throw notFound();
        if (projectId != null && context.canAccessProject(projectId) && projectCode == null && projectName == null)
            return List.of(new Reference(projectId, null, null));
        List<ProjectQueryCondition> conditions = new ArrayList<>();
        List<ProjectQueryFilter> groups = new ArrayList<>();
        if (projectId != null) {
            if (!context.canAccessProject(projectId)) groups.add(new ProjectQueryFilter("OR", List.of(
                    new ProjectQueryCondition("projectCode", "eq", projectId),
                    new ProjectQueryCondition("projectName", "eq", projectId)), List.of()));
        }
        if (projectCode != null) conditions.add(new ProjectQueryCondition("projectCode", "eq", projectCode));
        if (projectName != null) conditions.add(new ProjectQueryCondition("projectName", "eq", projectName));
        boolean trustedId = projectId != null && context.canAccessProject(projectId);
        ToolContext lookupContext = trustedId ? new ToolContext(context.tenantId(), context.userId(),
                context.identityId(), context.roleIds(), java.util.Set.of(projectId), context.permissions(),
                context.modelBinding()) : context;
        ProjectQueryResult result = projects.query(lookupContext, new ProjectQueryInput(
                List.of("projectCode", "projectName"),
                new ProjectQueryFilter("AND", conditions, groups), List.of(), List.of(), List.of(), 1, 6));
        if (result == null) throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY,
                "Project reference lookup returned no response");
        List<Reference> matches = new ArrayList<>();
        for (var row : result.rows()) {
            String id = text(row.get("projectId"));
            String code = text(row.get("projectCode"));
            String name = text(row.get("projectName"));
            if (id == null || !lookupContext.canAccessProject(id))
                throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN", HttpStatus.FORBIDDEN,
                        "Resolved project is outside trusted scope");
            // Validate the business response too: a fuzzy match must not become an exact selection.
            if (projectId != null && !(trustedId ? projectId.equals(id)
                    : projectId.equals(code) || projectId.equals(name))
                    || projectCode != null && !projectCode.equals(code)
                    || projectName != null && !projectName.equals(name)) continue;
            Reference reference = new Reference(id, code, name);
            if (matches.stream().noneMatch(item -> item.projectId().equals(id))) matches.add(reference);
        }
        if (matches.isEmpty()) throw notFound();
        if (matches.size() == 1 && (Boolean.TRUE.equals(result.hasNext())
                || result.total() != null && result.total() > 1))
            throw new AgentException("AGENT_PROJECT_REFERENCE_AMBIGUOUS", HttpStatus.BAD_REQUEST,
                    "Please narrow the project reference");
        return List.copyOf(matches);
    }

    private AgentException notFound() {
        return new AgentException("AGENT_PROJECT_REFERENCE_NOT_FOUND", HttpStatus.NOT_FOUND,
                "No accessible project matches the reference");
    }

    private String text(Object value) { return value == null ? null : value.toString(); }
}
