package com.smart.agent.tool.contract;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ConstructionContractDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConstructionContractClarifyDateTool implements AgentTool<ConstructionContractClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "constructionContract.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "施工合同查询涉及日期但无法确认日期维度时，向用户提供三种日期口径供确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = ConstructionContractDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选施工合同？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期（原经营看板口径）", domain),
                new ClarificationPrompt.Option("date:signingDate", "合同签订日期", domain),
                new ClarificationPrompt.Option("date:createDate", "合同创建日期", domain)));
    }
}
