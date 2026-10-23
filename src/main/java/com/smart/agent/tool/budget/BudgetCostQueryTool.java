package com.smart.agent.tool.budget;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.BudgetCostDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BudgetCostQueryTool implements AgentTool<BudgetCostQueryInput,Object> {
    private final BudgetCostBusinessClient client;
    private final ProjectReferenceResolver projects;
    public BudgetCostQueryTool(BudgetCostBusinessClient client,ProjectReferenceResolver projects){this.client=client;this.projects=projects;}
    @Override public String key(){return "budgetCost.query";}
    @Override public Class<BudgetCostQueryInput> inputType(){return BudgetCostQueryInput.class;}
    @Override public String requiredPermission(){return "menu:budget:cost";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public Optional<String> projectId(BudgetCostQueryInput input){return Optional.empty();}
    @Override public String description(){return "实时查询预算成本，summary全部可见项目合计，detail按项目分页，items单个项目合并后的成本行。"
            +"只用审批完成的预算编制、新增预算和最新有效变更，不把全部版本相加。items须明确一个项目。"
            +"不支持利润；两组当前预算比较使用budgetCost.compare，不支持同比环比；日期仅支持按项目创建范围选择项目后的当前累计预算，不是历史时点或期间预算。日期不明确先调用budgetCost.clarifyDate。"
            +"序号追问从可信项目引用取真实projectId重查，不复用历史金额。";}
    @Override public String argumentsSchemaJson(){return """
            {"type":"object","properties":{
            "projectId":{"type":"string","description":"真实项目ID"},
            "projectCode":{"type":"string","description":"精确项目编号"},
            "projectName":{"type":"string","description":"默认精确名称，同名先确认"},
            "projectNameMatch":{"type":"string","description":"仅exact精确或contains包含，只有明确要求包含才用contains"},
            "mode":{"type":"string","description":"仅summary全量统计、detail项目列表、items单项目成本行"},
            "dateField":{"type":"string","description":"仅projectCreateDate项目创建日期，用户未确认日期维度不得默认填入"},
            "startDate":{"type":"string"},"endDate":{"type":"string"},
            "page":{"type":"integer"},"pageSize":{"type":"integer"}},"additionalProperties":false}
            """;}
    @Override public Object execute(BudgetCostQueryInput input,ToolContext context){
        if(!context.permissions().contains(BudgetCostDomainContributor.PERMISSION))
            throw new com.smart.agent.common.error.AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"Budget archive menu required");
        if("contains".equals(input.projectNameMatch()) || input.projectId()==null && input.projectCode()==null && input.projectName()==null)
            return client.query(context,input);
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("匹配到多个项目，请确认要查询哪一个项目：","PROJECT_REFERENCE",
                matches.stream().limit(5).map(p->new ClarificationPrompt.Option("project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",BudgetCostDomainContributor.CODE)).toList());
        String id=matches.getFirst().projectId();
        var result=client.query(context,input.withProjectId(id));
        var filters=new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field","projectId","op","eq","value",id));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
}
