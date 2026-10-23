package com.smart.agent.tool.budget;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.BudgetCostDomainContributor;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BudgetCostCompareTool implements AgentTool<BudgetCostCompareInput,Object> {
    private final BudgetCostBusinessClient client;
    private final ProjectReferenceResolver projects;
    public BudgetCostCompareTool(BudgetCostBusinessClient client,ProjectReferenceResolver projects) {
        this.client=client;this.projects=projects;
    }
    @Override public String key(){return "budgetCost.compare";}
    @Override public Class<BudgetCostCompareInput> inputType(){return BudgetCostCompareInput.class;}
    @Override public String requiredPermission(){return BudgetCostDomainContributor.PERMISSION;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "实时自定义比较两组项目的当前项目数量与合并预算成本，comparisonType仅CUSTOM；current第一组和baseline第二组都必须明确。"
            +"复用已审批预算编制、新增预算与最新有效变更，不累加历次版本。两组同一项目创建日期维度，可不限定日期。"
            +"日期只选择项目，不是预算发生期间或历史快照，不支持同比环比。含义不明先clarifyDate，不得猜测历史金额。"
            +"服务端计算第一组减第二组及增长率，零基准不可计算；全部匹配项目全量汇总，不比较分页小计。"
            +"需要目标成本档案菜单及当前可见项目范围，项目名称默认精确，同名先确认，只有明确包含才用contains。"
            +"查看某组项目列表使用query detail，保留该组项目筛选与日期；再查单项目预算明细用真实projectId及items。组别不明先追问，每轮重新调用。";}
    @Override public String argumentsSchemaJson(){
        String group="""
            {"type":"object","properties":{
              "projectId":{"type":"string","description":"可信真实项目ID"},
              "projectCode":{"type":"string","description":"精确项目编号"},
              "projectName":{"type":"string","description":"默认精确项目名称，同名先确认"},
              "projectNameMatch":{"type":"string","description":"仅exact精确或contains包含；只有用户明确要求包含才用contains"},
              "dateField":{"type":"string","description":"仅projectCreateDate项目创建日期，含义不清先追问"},
              "startDate":{"type":"string","description":"yyyy-MM-dd，含当天"},
              "endDate":{"type":"string","description":"yyyy-MM-dd，含当天"}
            },"additionalProperties":false}
            """;
        return "{\"type\":\"object\",\"properties\":{\"comparisonType\":{\"type\":\"string\",\"description\":\"仅CUSTOM自定义两组当前合并预算\"},"
                +"\"current\":"+group+",\"baseline\":"+group+"},\"required\":[\"comparisonType\",\"current\",\"baseline\"],\"additionalProperties\":false}";
    }
    @Override public Object execute(BudgetCostCompareInput input,ToolContext context){
        if(!context.permissions().contains(BudgetCostDomainContributor.PERMISSION))
            throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号暂未开通预算成本查看权限，暂时无法比较预算成本");
        Object first=resolve(input.current(),"current",context);if(first instanceof ClarificationPrompt)return first;
        Object second=resolve(input.baseline(),"baseline",context);if(second instanceof ClarificationPrompt)return second;
        var a=(BudgetCostQueryInput)first;var b=(BudgetCostQueryInput)second;
        var result=client.compare(context,new BudgetCostCompareInput("CUSTOM",a,b));
        var filters=new ArrayList<>(result.appliedFilters());
        if(a.projectId()!=null)filters.add(Map.of("comparisonGroup","current","projectId",a.projectId()));
        if(b.projectId()!=null)filters.add(Map.of("comparisonGroup","baseline","projectId",b.projectId()));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
    private Object resolve(BudgetCostQueryInput input,String group,ToolContext context){
        if("contains".equals(input.projectNameMatch()) || input.projectId()==null && input.projectCode()==null && input.projectName()==null)return input;
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("请确认"+("current".equals(group)?"第一组":"第二组")+"要比较的项目：",
                "COMPARISON_PROJECT_REFERENCE",matches.stream().limit(5).map(p->new ClarificationPrompt.Option(
                        group+":project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",BudgetCostDomainContributor.CODE)).toList());
        return input.withProjectId(matches.getFirst().projectId());
    }
}
