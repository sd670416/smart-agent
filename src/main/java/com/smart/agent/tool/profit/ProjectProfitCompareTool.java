package com.smart.agent.tool.profit;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.ProjectProfitDomainContributor;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectProfitCompareTool implements AgentTool<ProjectProfitCompareInput,Object> {
    private final ProjectProfitBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectProfitCompareTool(ProjectProfitBusinessClient client,ProjectReferenceResolver projects) {
        this.client=client;this.projects=projects;
    }
    @Override public String key(){return "projectProfit.compare";}
    @Override public Class<ProjectProfitCompareInput> inputType(){return ProjectProfitCompareInput.class;}
    @Override public String requiredPermission(){return "";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "实时自定义比较两组项目当前预算利润或实际收支利润，comparisonType仅CUSTOM；current第一组、baseline第二组必须明确且使用同一metric。"
            +"budget预算利润=合同最终金额-合并预算；actual实际收支利润=收入-净付款；both同时比较。亏损保留，退款仅抵减一次。利润率按各组全量总额计算，不平均项目率；利润率差值用百分点，不计算利润率增长率，零分母不可比较。"
            +"日期仅按项目创建范围选项目，不是期间收支或历史余额，不支持同比环比；日期或组别不明确先追问。"
            +"接口全量计算数量、金额、差额及增长率，金额零基准无法计算，负基数结合调整理解；利润率差值是百分点，不能相加分页。必须具备指标全部来源菜单及可见项目权限。"
            +"项目名称默认精确，同名先确认，只有明确包含才用contains。按组查看项目列表用projectProfit.query detail，保留该组项目、名称条件、指标及日期并实时查询；再按真实projectId查看项目分项。";}
    @Override public String argumentsSchemaJson(){
        String group="""
            {"type":"object","properties":{
              "projectId":{"type":"string","description":"可信真实项目ID"},
              "projectCode":{"type":"string","description":"精确项目编号"},
              "projectName":{"type":"string","description":"默认精确项目名称，同名先确认"},
              "projectNameMatch":{"type":"string","description":"仅exact或contains；明确包含才用contains"},
              "metric":{"type":"string","description":"仅budget预算利润、actual实际收支利润或both两者；两组相同，默认both"},
              "dateField":{"type":"string","description":"仅projectCreateDate项目创建日期，含义不清先确认"},
              "startDate":{"type":"string","description":"yyyy-MM-dd，含当天"},
              "endDate":{"type":"string","description":"yyyy-MM-dd，含当天"}
            },"additionalProperties":false}
            """;
        return "{\"type\":\"object\",\"properties\":{\"comparisonType\":{\"type\":\"string\",\"description\":\"仅CUSTOM自定义当前累计比较\"},"
                +"\"current\":"+group+",\"baseline\":"+group+"},\"required\":[\"comparisonType\",\"current\",\"baseline\"],\"additionalProperties\":false}";
    }
    @Override public Object execute(ProjectProfitCompareInput input,ToolContext context){
        if(!context.permissions().containsAll(ProjectProfitDomainContributor.permissions(input.current().metric())))
            throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号尚未具备这项利润统计所需的全部查看权限，暂时无法提供完整比较");
        Object first=resolve(input.current(),"current",context);if(first instanceof ClarificationPrompt)return first;
        Object second=resolve(input.baseline(),"baseline",context);if(second instanceof ClarificationPrompt)return second;
        var a=(ProjectProfitQueryInput)first;var b=(ProjectProfitQueryInput)second;
        var result=client.compare(context,new ProjectProfitCompareInput("CUSTOM",a,b));
        var filters=new ArrayList<>(result.appliedFilters());
        if(a.projectId()!=null)filters.add(Map.of("comparisonGroup","current","projectId",a.projectId()));
        if(b.projectId()!=null)filters.add(Map.of("comparisonGroup","baseline","projectId",b.projectId()));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
    private Object resolve(ProjectProfitQueryInput input,String group,ToolContext context){
        if("contains".equals(input.projectNameMatch()) || input.projectId()==null && input.projectCode()==null && input.projectName()==null)return input;
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("请确认"+("current".equals(group)?"第一组":"第二组")+"要比较的项目：",
                "COMPARISON_PROJECT_REFERENCE",matches.stream().limit(5).map(p->new ClarificationPrompt.Option(
                        group+":project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",ProjectProfitDomainContributor.CODE)).toList());
        return input.withProjectId(matches.getFirst().projectId());
    }
}
