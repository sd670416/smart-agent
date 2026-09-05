package com.smart.agent.authorization;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/roles/{roleId}/knowledge-grants")
public class RoleKnowledgeGrantController {
    private final RoleKnowledgeGrantService service;
    public RoleKnowledgeGrantController(RoleKnowledgeGrantService service) { this.service = service; }
    @GetMapping
    public Set<UUID> get(@PathVariable String roleId, @RequestAttribute(AgentUserContext.class.getName()) AgentUserContext actor) {
        require(actor); return service.grantsForRole(actor.tenantId(), roleId);
    }
    @PutMapping
    public Set<UUID> put(@PathVariable String roleId, @RequestBody Set<UUID> ids, @RequestAttribute(AgentUserContext.class.getName()) AgentUserContext actor) {
        require(actor); return service.replaceGrants(actor.tenantId(), roleId, ids, actor);
    }
    private static void require(AgentUserContext actor) { if (actor == null || !actor.permissions().contains("ai:knowledge:manage")) throw new IllegalStateException("ai:knowledge:manage permission is required"); }
}
