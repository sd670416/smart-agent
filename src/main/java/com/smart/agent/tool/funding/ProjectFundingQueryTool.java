package com.smart.agent.tool.funding;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectFundingDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProjectFundingQueryTool implements AgentTool<ProjectFundingQueryInput,Object> {
    private final ProjectFundingBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectFundingQueryTool(ProjectFundingBusinessClient client,ProjectReferenceResolver projects){this.client=client;this.projects=projects;}
    @Override public String key(){return "projectFunding.query";}
    @Override public Class<ProjectFundingQueryInput> inputType(){return ProjectFundingQueryInput.class;}
    @Override public String requiredPermission(){return "menu:finance:payment-request";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public Optional<String> projectId(ProjectFundingQueryInput input){return Optional.empty();}
    @Override public String description(){return "实时查询累计付款、净付款和项目垫资。summary全范围汇总，detail按项目分页。"
            +"累计付款=付款申请+其他付款申请+项目报销；净付款再减材料退货退款；垫资逐项目减已收项目款后负值归零再汇总。"
            +"不是实际银行支付。缺少必要来源权限拒绝返回完整统计。不支持利润或比较。日期仅支持项目创建范围内项目的累计金额，"
            +"不能当作期间收支；日期含义不明调用projectFunding.clarifyDate追问用户，相对日期先取当前时间。按序号追问须使用上次列表真实projectId并重新查询。";}
    @Override public String argumentsSchemaJson(){return """
            {"type":"object","properties":{
            "projectId":{"type":"string","description":"真实项目ID"},
            "projectCode":{"type":"string","description":"精确项目编号"},
            "projectName":{"type":"string","description":"默认精确名称，同名先确认"},
            "projectNameMatch":{"type":"string","enum":["exact","contains"],"description":"只有明确要求包含才用contains"},
            "mode":{"type":"string","enum":["summary","detail"],"description":"全量统计或按项目列表"},
            "metric":{"type":"string","enum":["cumulative","net","funding"],"description":"累计付款、净付款、项目垫资"},
            "dateField":{"type":"string","enum":["projectCreateDate"],"description":"仅项目创建日期，用户未确认日期维度不得默认填入"},
            "startDate":{"type":"string"},"endDate":{"type":"string"},
            "page":{"type":"integer"},"pageSize":{"type":"integer"}},"additionalProperties":false}
            """;}
    @Override public Object execute(ProjectFundingQueryInput input,ToolContext context){
        if(!context.permissions().containsAll(ProjectFundingDomainContributor.permissions(input.metric())))
            throw new com.smart.agent.common.error.AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"Required funding source menus missing");
        if("contains".equals(input.projectNameMatch()) || input.projectId()==null && input.projectCode()==null && input.projectName()==null)
            return client.query(context,input);
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("匹配到多个项目，请确认要查询哪一个项目：","PROJECT_REFERENCE",
                matches.stream().limit(5).map(p->new ClarificationPrompt.Option("project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",ProjectFundingDomainContributor.CODE)).toList());
        String id=matches.getFirst().projectId();
        var result=client.query(context,input.withProjectId(id));
        var filters=new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field","projectId","op","eq","value",id));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
}
