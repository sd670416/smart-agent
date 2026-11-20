package com.smart.agent.routing;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectRiskDomainContributor implements BusinessDomainContributor {
    public static boolean matches(String q) {
        return q != null && (q.contains("风险") || q.contains("隐患") || q.contains("整改")
                || q.contains("工期") || q.contains("施工多少天") || q.contains("施工多久") || q.contains("进度延期")
                || q.contains("安全问题") || q.contains("质量问题"));
    }
    @Override public BusinessDomainDescriptor descriptor() {
        return new BusinessDomainDescriptor("PROJECT_RISK", "项目风险与工期", List.of("projectRisk.query"),
                List.of(new DataScopeDescriptor("RISK_SOURCES", "各风险来源菜单及可见项目", List.of("MENU", "PROJECT"))),
                "REALTIME", List.of("经营风险", "质量风险", "安全风险", "进度风险", "工期", "整改"));
    }
}
