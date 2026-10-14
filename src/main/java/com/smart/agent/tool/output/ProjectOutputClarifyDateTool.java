package com.smart.agent.tool.output;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectOutputDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectOutputClarifyDateTool implements AgentTool<ProjectOutputClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "projectOutput.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "工程产值查询涉及日期但无法确认日期维度时，向用户提供两种日期口径供确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = ProjectOutputDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选工程产值？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "产值单创建日期", domain)));
    }
}

