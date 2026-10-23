package com.smart.agent.tool.cost;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.CostContractDomainContributor;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CostContractCompareTool implements AgentTool<CostContractCompareInput,Object> {
    private final CostContractBusinessClient client;
    private final ProjectReferenceResolver projects;
    public CostContractCompareTool(CostContractBusinessClient client,ProjectReferenceResolver projects) {
        this.client=client;this.projects=projects;
    }
    @Override public String key(){return "costContract.compare";}
    @Override public Class<CostContractCompareInput> inputType(){return CostContractCompareInput.class;}
    @Override public String requiredPermission(){return null;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "实时自定义比较两组项目当前的五类成本合同结构，comparisonType仅CUSTOM；current第一组、baseline第二组都必须明确。"
            +"材料、劳务、机械、专业分包、其他支出合同的数量、合同金额和金额占比，完整五类分母，必须具备全部五类权限。"
            +"仅项目创建日期projectCreateDate筛选项目后的当前合同，不是合同发生期间或历史快照，不支持同比环比。日期含义不清先clarifyDate，不得猜测。"
            +"两组同一日期维度，日期范围成对提供；项目名称精确匹配，同名先确认。金额不是实际付款。"
            +"服务端计算第一组减第二组及增长率，占比差额为百分点，不再乘100；零分母不可计算，不冒充0%。"
            +"查看某组某类明细时先明确第一组或第二组，使用query detail保留该组项目、合同名称和日期条件及真实source；每轮重新调用。";}
    @Override public String argumentsSchemaJson(){
        String group="""
            {"type":"object","properties":{
              "projectId":{"type":"string","description":"可信真实项目ID"},
              "projectCode":{"type":"string","description":"精确项目编号"},
              "projectName":{"type":"string","description":"精确项目名称，同名先确认"},
              "contractName":{"type":"string","description":"明确要求名称包含时筛选"},
              "dateField":{"type":"string","description":"仅projectCreateDate项目创建日期，含义不清先追问"},
              "startDate":{"type":"string","description":"yyyy-MM-dd，含当天"},
              "endDate":{"type":"string","description":"yyyy-MM-dd，含当天"}
            },"additionalProperties":false}
            """;
        return "{\"type\":\"object\",\"properties\":{\"comparisonType\":{\"type\":\"string\",\"description\":\"仅CUSTOM自定义两组当前合同结构\"},"
                +"\"current\":"+group+",\"baseline\":"+group+"},\"required\":[\"comparisonType\",\"current\",\"baseline\"],\"additionalProperties\":false}";
    }
    @Override public Object execute(CostContractCompareInput input,ToolContext context){
        if(!context.permissions().containsAll(CostContractDomainContributor.permissions("all")))
            throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号尚未开通全部五类合同权限，暂时无法完整比较合同结构");
        Object first=resolve(input.current(),"current",context);
        if(first instanceof ClarificationPrompt)return first;
        Object second=resolve(input.baseline(),"baseline",context);
        if(second instanceof ClarificationPrompt)return second;
        var a=(CostContractQueryInput)first;var b=(CostContractQueryInput)second;
        var result=client.compare(context,new CostContractCompareInput("CUSTOM",a,b));
        var filters=new ArrayList<>(result.appliedFilters());
        if(a.projectId()!=null)filters.add(Map.of("comparisonGroup","current","projectId",a.projectId()));
        if(b.projectId()!=null)filters.add(Map.of("comparisonGroup","baseline","projectId",b.projectId()));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
    private Object resolve(CostContractQueryInput input,String group,ToolContext context){
        if(input.projectId()==null && input.projectCode()==null && input.projectName()==null)return input;
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("请确认"+("current".equals(group)?"第一组":"第二组")+"要比较的项目：",
                "COMPARISON_PROJECT_REFERENCE",matches.stream().limit(5).map(p->new ClarificationPrompt.Option(
                        group+":project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",CostContractDomainContributor.CODE)).toList());
        return input.withProjectId(matches.getFirst().projectId());
    }
}
