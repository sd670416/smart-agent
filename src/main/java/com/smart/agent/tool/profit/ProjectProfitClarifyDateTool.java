package com.smart.agent.tool.profit;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectProfitDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectProfitClarifyDateTool implements AgentTool<ProjectProfitClarifyDateTool.Input,ClarificationPrompt> {
    public record Input() {}
    @Override public String key(){return "projectProfit.clarifyDate";}
    @Override public Class<Input> inputType(){return Input.class;}
    @Override public String requiredPermission(){return null;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "项目利润查询日期含义不明确时先确认；目前只支持按项目创建日期选择项目后查询累计金额，不支持期间收支，不读取业务金额。";}
    @Override public ClarificationPrompt execute(Input input,ToolContext context){
        return new ClarificationPrompt("您是想筛选这段时间创建的项目，再查看这些项目的当前累计利润吗？目前暂不支持按收支发生期间统计利润。",
                "DATE_FIELD",List.of(new ClarificationPrompt.Option("date:projectCreateDate","按项目创建日期筛选",ProjectProfitDomainContributor.CODE),
                        new ClarificationPrompt.Option("date:allProjectDates","不限定日期，查看累计金额",ProjectProfitDomainContributor.CODE)));
    }
}
