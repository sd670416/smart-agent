package com.smart.agent.authorization;

import com.smart.agent.security.AgentUserContext;
import com.smart.agent.knowledge.manage.KnowledgeForbiddenException;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.smart.agent.common.api.ApiError;

@RestController
@RequestMapping("/ai/roles/{roleId}/knowledge-grants")
public class RoleKnowledgeGrantController {
    private final RoleKnowledgeGrantService service;
    public RoleKnowledgeGrantController(RoleKnowledgeGrantService service) { this.service = service; }
    @GetMapping
    public Set<UUID> get(@PathVariable String roleId, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext actor) {
        require(actor); return service.grantsForRole(actor.tenantId(), roleId);
    }
    @PutMapping
    public Set<UUID> put(@PathVariable String roleId, @RequestBody Set<UUID> ids, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext actor) {
        require(actor); return service.replaceGrants(actor.tenantId(), roleId, ids, actor);
    }
    private static void require(AgentUserContext actor) { if (actor == null || !actor.permissions().contains("ai:knowledge:manage")) throw new KnowledgeForbiddenException(); }
    @ExceptionHandler(KnowledgeForbiddenException.class)
    ResponseEntity<ApiError> forbidden(KnowledgeForbiddenException e) { return ResponseEntity.status(403).body(new ApiError("KNOWLEDGE_FORBIDDEN", e.getMessage(), null)); }
}
