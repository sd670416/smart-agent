package com.smart.agent.tool.disbursement;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectReimburseDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectReimburseClarifyDateTool implements AgentTool<ProjectReimburseClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "projectReimburse.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:finance:project-reimburse"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "项目报销查询的日期含义不明确时，提供申请创建日期、项目创建日期、报销日期供用户确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = ProjectReimburseDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选项目报销？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "申请创建日期", domain),
                new ClarificationPrompt.Option("date:reimburseDate", "报销日期", domain)));
    }
}
