package com.smart.agent.tool.material;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.MaterialRefundDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MaterialRefundClarifyDateTool implements AgentTool<MaterialRefundClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "materialRefund.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:material:refund"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "材料退货退款查询的日期含义不明确时，提供单据创建日期、项目创建日期、退货时间供用户确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = MaterialRefundDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选材料退货退款？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "单据创建日期", domain),
                new ClarificationPrompt.Option("date:returnDate", "退货时间", domain)));
    }
}
