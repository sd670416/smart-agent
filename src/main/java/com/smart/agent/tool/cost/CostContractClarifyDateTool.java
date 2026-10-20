package com.smart.agent.tool.cost;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.CostContractDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CostContractClarifyDateTool implements AgentTool<CostContractClarifyDateTool.Input,ClarificationPrompt> {
    public record Input() {}
    @Override public String key(){return "costContract.clarifyDate";}
    @Override public Class<Input> inputType(){return Input.class;}
    @Override public String requiredPermission(){return null;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "成本合同查询日期不明确时先确认。只支持按项目创建日期选择项目后查看当前合同金额，不支持按合同发生期间或历史时点统计；不读取金额。";}
    @Override public ClarificationPrompt execute(Input input,ToolContext context){
        if(CostContractDomainContributor.permissions("all").stream().noneMatch(context.permissions()::contains))
            throw new com.smart.agent.common.error.AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"成本合同查看权限暂未开通");
        return new ClarificationPrompt("目前支持筛选这段时间创建的项目，再查看这些项目的当前合同金额。您希望如何查询？","DATE_FIELD",List.of(
                new ClarificationPrompt.Option("date:projectCreateDate","按项目创建日期筛选",CostContractDomainContributor.CODE),
                new ClarificationPrompt.Option("date:allProjectDates","不限定日期，查看当前累计金额",CostContractDomainContributor.CODE)));
    }
}
