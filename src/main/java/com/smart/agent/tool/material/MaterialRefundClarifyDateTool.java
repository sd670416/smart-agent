package com.smart.agent.tool.material;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.MaterialRefundDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MaterialRefundClarifyDateTool implements AgentTool<MaterialRefundClarifyDateTool.Input, ClarificationPrompt> {
    public record Input(String ambiguity) {
        public Input() { this(null); }
        public Input {
            ambiguity = ambiguity == null ? "FIELD" : ambiguity;
            if (!List.of("FIELD", "PERIOD").contains(ambiguity)) throw new IllegalArgumentException("Invalid date ambiguity");
        }
    }
    @Override public String key() { return "materialRefund.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:material:refund"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "材料退货退款查询的日期含义不明确时，提供申请创建日期、项目创建日期、明细退货时间供用户确认；比较缺少期间用ambiguity=PERIOD；不读取业务数据。"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"ambiguity\":{\"type\":\"string\",\"enum\":[\"FIELD\",\"PERIOD\"]}},\"additionalProperties\":false}";
    }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = MaterialRefundDomainContributor.CODE;
        if ("PERIOD".equals(input.ambiguity())) return new ClarificationPrompt("您希望比较哪个期间？可选择下面的期间，或直接补充具体起止日期。",
                "COMPARISON_PERIOD", List.of(new ClarificationPrompt.Option("period:MONTH", "本月截至今天", domain),
                new ClarificationPrompt.Option("period:QUARTER", "本季度截至今天", domain),
                new ClarificationPrompt.Option("period:YEAR", "今年截至今天", domain)));
        return new ClarificationPrompt("您希望按哪个日期筛选材料退货退款？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "申请创建日期", domain),
                new ClarificationPrompt.Option("date:returnDate", "明细退货时间", domain)));
    }
}
