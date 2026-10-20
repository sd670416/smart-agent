package com.smart.agent.tool.cost;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.CostContractDomainContributor;
import com.smart.agent.common.error.AgentException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CostContractQueryTool implements AgentTool<CostContractQueryInput,Object> {
    private final CostContractBusinessClient client;
    private final ProjectReferenceResolver projects;
    public CostContractQueryTool(CostContractBusinessClient client,ProjectReferenceResolver projects) {this.client=client;this.projects=projects;}
    @Override public String key(){return "costContract.query";}
    @Override public Class<CostContractQueryInput> inputType(){return CostContractQueryInput.class;}
    @Override public String requiredPermission(){return null;}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public Optional<String> projectId(CostContractQueryInput input){return Optional.empty();}
    @Override public String description(){return "实时查询审批完成的材料、劳务、机械、专业分包、其他支出合同。summary按类别全量统计合同数量、金额，all同时计算五类金额占比；detail合同明细分页。"
            +"各来源分别校验菜单权限，all必须具备五类权限，缺失来源不得当零。任何类别的占比都必须source=all取完整分母；单类查询不返回全来源占比。金额是合同金额，不是实际付款。"
            +"仅支持项目创建日期范围，日期含义不明确先调用costContract.clarifyDate，不得猜测合同签订日期。项目名称精确匹配，同名先确认。"
            +"查看某一条时使用真实contractId及source组合，不得只用序号。每轮实时调用，不从历史结果计算。待办、已办、我发起使用approval。"
            +"不支持期间比较；不支持的筛选或多来源部分组合先说明并确认，不能静默扩大查询。";}
    @Override public String argumentsSchemaJson(){return """
        {"type":"object","properties":{
        "projectId":{"type":"string","description":"可信真实项目ID"},
        "projectCode":{"type":"string","description":"精确项目编号"},
        "projectName":{"type":"string","description":"精确项目名称，同名追问"},
        "source":{"type":"string","enum":["all","material","labor","machine","major","other"],"description":"全部五类、材料、劳务、机械、专业分包、其他支出"},
        "mode":{"type":"string","enum":["summary","detail"]},
        "contractId":{"type":"string","description":"真实合同ID，需同时指定对应source"},
        "contractName":{"type":"string","description":"明确要求名称包含时筛选"},
        "dateField":{"type":"string","enum":["projectCreateDate"],"description":"仅项目创建日期，含义不清先追问"},
        "startDate":{"type":"string"},"endDate":{"type":"string"},
        "page":{"type":"integer"},"pageSize":{"type":"integer"}},"additionalProperties":false}
        """;}
    @Override public Object execute(CostContractQueryInput input,ToolContext context){
        if(!context.permissions().containsAll(CostContractDomainContributor.permissions(input.source())))
            throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号尚未开通所查询合同类别的权限，无法完整计算成本占比");
        if(input.projectId()==null && input.projectCode()==null && input.projectName()==null)return client.query(context,input);
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("匹配到多个项目，请确认要查看哪一个项目的成本合同：","PROJECT_REFERENCE",
                matches.stream().limit(5).map(p->new ClarificationPrompt.Option("project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",CostContractDomainContributor.CODE)).toList());
        String id=matches.getFirst().projectId();
        var result=client.query(context,input.withProjectId(id));
        var filters=new ArrayList<>(result.appliedFilters());filters.add(Map.of("field","projectId","op","eq","value",id));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
}
