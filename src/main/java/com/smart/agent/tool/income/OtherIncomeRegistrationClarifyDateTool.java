package com.smart.agent.tool.income;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.OtherIncomeRegistrationDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OtherIncomeRegistrationClarifyDateTool implements AgentTool<OtherIncomeRegistrationClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "otherIncomeRegistration.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:finance:other-income-registration"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "其他收入登记查询的日期含义不明确时，提供到账日期、登记创建日期、项目创建日期供用户确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = OtherIncomeRegistrationDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选其他收入登记？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "登记创建日期", domain),
                new ClarificationPrompt.Option("date:receivedDate", "到账日期", domain)));
    }
}
