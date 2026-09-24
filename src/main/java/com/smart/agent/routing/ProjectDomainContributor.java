package com.smart.agent.routing;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectDomainContributor implements BusinessDomainContributor {
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            "PROJECT", "项目", List.of("project.query", "project.listAccessible", "project.getOverview",
                    "project.getArchiveDetail", "project.getContracts"),
            List.of(new DataScopeDescriptor("PROJECT_MENU", "项目菜单权限", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", List.of("项目", "项目档案", "项目报备", "工程"));

    @Override
    public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
}
