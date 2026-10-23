package com.smart.agent.tool.funding;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.ProjectFundingDomainContributor;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectFundingCompareTool implements AgentTool<ProjectFundingCompareInput,Object> {
    private final ProjectFundingBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectFundingCompareTool(ProjectFundingBusinessClient client,ProjectReferenceResolver projects) {
        this.client=client;this.projects=projects;
    }
    @Override public String key(){return "projectFunding.compare";}
    @Override public Class<ProjectFundingCompareInput> inputType(){return ProjectFundingCompareInput.class;}
    @Override public String requiredPermission(){return "menu:finance:payment-request";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "实时自定义比较两组项目当前累计付款、净付款或垫资，comparisonType仅CUSTOM；current第一组、baseline第二组必须明确且使用同一metric。"
            +"cumulative累计三类付款；net再减项目仓退款；funding再减已收项目款，逐项目负值归零再合计，不得跨项目抵消。"
            +"日期仅按项目创建范围选项目，不是期间收支或历史余额，不支持同比环比；日期或组别不明确先追问。"
            +"接口全量计算数量、金额、差额及增长率，零基准无法计算，不能相加分页。必须具备指标全部来源菜单及可见项目权限。"
            +"项目名称默认精确，同名先确认，只有明确包含才用contains。按组查看项目列表用projectFunding.query detail，保留该组项目、名称条件、指标及日期并实时查询；再按真实projectId查看项目分项。";}
    @Override public String argumentsSchemaJson(){
        String group="""
            {"type":"object","properties":{
              "projectId":{"type":"string","description":"可信真实项目ID"},
              "projectCode":{"type":"string","description":"精确项目编号"},
              "projectName":{"type":"string","description":"默认精确项目名称，同名先确认"},
              "projectNameMatch":{"type":"string","description":"仅exact或contains；明确包含才用contains"},
              "metric":{"type":"string","description":"仅cumulative累计付款、net净付款或funding项目垫资；两组相同，默认funding"},
              "dateField":{"type":"string","description":"仅projectCreateDate项目创建日期，含义不清先确认"},
              "startDate":{"type":"string","description":"yyyy-MM-dd，含当天"},
              "endDate":{"type":"string","description":"yyyy-MM-dd，含当天"}
            },"additionalProperties":false}
            """;
        return "{\"type\":\"object\",\"properties\":{\"comparisonType\":{\"type\":\"string\",\"description\":\"仅CUSTOM自定义当前累计比较\"},"
                +"\"current\":"+group+",\"baseline\":"+group+"},\"required\":[\"comparisonType\",\"current\",\"baseline\"],\"additionalProperties\":false}";
    }
    @Override public Object execute(ProjectFundingCompareInput input,ToolContext context){
        if(!context.permissions().containsAll(ProjectFundingDomainContributor.permissions(input.current().metric())))
            throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号尚未具备这项付款或垫资统计所需的全部查看权限，暂时无法提供完整比较");
        Object first=resolve(input.current(),"current",context);if(first instanceof ClarificationPrompt)return first;
        Object second=resolve(input.baseline(),"baseline",context);if(second instanceof ClarificationPrompt)return second;
        var a=(ProjectFundingQueryInput)first;var b=(ProjectFundingQueryInput)second;
        var result=client.compare(context,new ProjectFundingCompareInput("CUSTOM",a,b));
        var filters=new ArrayList<>(result.appliedFilters());
        if(a.projectId()!=null)filters.add(Map.of("comparisonGroup","current","projectId",a.projectId()));
        if(b.projectId()!=null)filters.add(Map.of("comparisonGroup","baseline","projectId",b.projectId()));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
    private Object resolve(ProjectFundingQueryInput input,String group,ToolContext context){
        if("contains".equals(input.projectNameMatch()) || input.projectId()==null && input.projectCode()==null && input.projectName()==null)return input;
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("请确认"+("current".equals(group)?"第一组":"第二组")+"要比较的项目：",
                "COMPARISON_PROJECT_REFERENCE",matches.stream().limit(5).map(p->new ClarificationPrompt.Option(
                        group+":project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",ProjectFundingDomainContributor.CODE)).toList());
        return input.withProjectId(matches.getFirst().projectId());
    }
}
