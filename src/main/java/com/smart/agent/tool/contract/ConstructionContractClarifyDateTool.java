package com.smart.agent.tool.contract;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ConstructionContractDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConstructionContractClarifyDateTool implements AgentTool<ConstructionContractClarifyDateTool.Input, ClarificationPrompt> {
    public record Input(String ambiguity) {
        public Input() { this(null); }
        public Input {
            ambiguity = ambiguity == null ? "FIELD" : ambiguity;
            if (!List.of("FIELD", "PERIOD").contains(ambiguity)) throw new IllegalArgumentException("Invalid date ambiguity");
        }
    }
    @Override public String key() { return "constructionContract.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "施工合同查询的日期含义不明确用ambiguity=FIELD（默认），提供日期字段选择；同比环比缺少比较期间用PERIOD，提供本月、本季度、今年截至今天选项，用户也可补充自定义日期；不读取业务数据。"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"ambiguity\":{\"type\":\"string\",\"description\":\"FIELD日期含义，PERIOD比较期间\"}},\"additionalProperties\":false}";
    }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        if (!context.permissions().contains("menu:project"))
            throw new com.smart.agent.common.error.AgentException("AGENT_PROJECT_MENU_FORBIDDEN",
                    org.springframework.http.HttpStatus.FORBIDDEN, "Project menu permission required");
        String domain = ConstructionContractDomainContributor.CODE;
        if ("PERIOD".equals(input.ambiguity())) return new ClarificationPrompt("您希望比较哪个期间？可选择下面的期间，或直接补充具体起止日期。",
                "COMPARISON_PERIOD", List.of(new ClarificationPrompt.Option("period:MONTH", "本月截至今天", domain),
                new ClarificationPrompt.Option("period:QUARTER", "本季度截至今天", domain),
                new ClarificationPrompt.Option("period:YEAR", "今年截至今天", domain)));
        return new ClarificationPrompt("您希望按哪个日期筛选施工合同？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "合同创建日期", domain),
                new ClarificationPrompt.Option("date:signingDate", "合同签订日期", domain)));
    }
}
