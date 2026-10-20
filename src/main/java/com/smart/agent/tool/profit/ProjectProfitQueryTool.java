package com.smart.agent.tool.profit;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectProfitDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProjectProfitQueryTool implements AgentTool<ProjectProfitQueryInput,Object> {
    private final ProjectProfitBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectProfitQueryTool(ProjectProfitBusinessClient client,ProjectReferenceResolver projects){this.client=client;this.projects=projects;}
    @Override public String key(){return "projectProfit.query";}
    @Override public Class<ProjectProfitQueryInput> inputType(){return ProjectProfitQueryInput.class;}
    @Override public String requiredPermission(){return null;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public Optional<String> projectId(ProjectProfitQueryInput input){return Optional.empty();}
    @Override public String description(){return "实时查询预算利润、实际收支利润和利润率，summary全量汇总，detail项目分页并展示计算分项。"
            +"budget预算利润=施工合同最终金额-合并预算成本；actual实际收支利润=已收项目款-净付款，净付款已减退款，不二次减退款。both同时展示。"
            +"预算利润率分母是施工合同最终金额，实际利润率分母是已收项目款，保留亏损负值；分母零显示暂无法计算，汇总率按总金额计算不是平均项目率。"
            +"实际利润是审批记录的收支估算，不是会计核算利润。按metric验证全部来源权限。"
            +"不支持比较。日期仅项目创建范围内项目的当前累计利润，不是期间利润；含义不明用projectProfit.clarifyDate。序号按可信真实projectId重查。";}
    @Override public String argumentsSchemaJson(){return """
            {"type":"object","properties":{
            "projectId":{"type":"string","description":"真实项目ID"},
            "projectCode":{"type":"string","description":"精确项目编号"},
            "projectName":{"type":"string","description":"默认精确名称，同名先确认"},
            "projectNameMatch":{"type":"string","enum":["exact","contains"],"description":"只有明确要求包含才用contains"},
            "mode":{"type":"string","enum":["summary","detail"],"description":"全量统计或按项目列表"},
            "metric":{"type":"string","enum":["budget","actual","both"],"description":"预算利润、实际利润、全部利润分项"},
            "dateField":{"type":"string","enum":["projectCreateDate"],"description":"仅项目创建日期，用户未确认日期维度不得默认填入"},
            "startDate":{"type":"string"},"endDate":{"type":"string"},
            "page":{"type":"integer"},"pageSize":{"type":"integer"}},"additionalProperties":false}
            """;}
    @Override public Object execute(ProjectProfitQueryInput input,ToolContext context){
        if(!context.permissions().containsAll(ProjectProfitDomainContributor.permissions(input.metric())))
            throw new com.smart.agent.common.error.AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"Required profit source menus missing");
        if("contains".equals(input.projectNameMatch()) || input.projectId()==null && input.projectCode()==null && input.projectName()==null)
            return client.query(context,input);
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("匹配到多个项目，请确认要查询哪一个项目：","PROJECT_REFERENCE",
                matches.stream().limit(5).map(p->new ClarificationPrompt.Option("project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",ProjectProfitDomainContributor.CODE)).toList());
        String id=matches.getFirst().projectId();
        var result=client.query(context,input.withProjectId(id));
        var filters=new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field","projectId","op","eq","value",id));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
}
