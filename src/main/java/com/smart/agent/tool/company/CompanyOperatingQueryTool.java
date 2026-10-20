package com.smart.agent.tool.company;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.CompanyOperatingDomainContributor;
import com.smart.agent.common.error.AgentException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
@Component
public class CompanyOperatingQueryTool implements AgentTool<CompanyOperatingQueryInput,Object> {
    private final CompanyOperatingBusinessClient client;
    private final ProjectReferenceResolver projects;
    public CompanyOperatingQueryTool(CompanyOperatingBusinessClient client,ProjectReferenceResolver projects){this.client=client;this.projects=projects;}
    @Override public String key(){return "companyOperating.query";}
    @Override public Class<CompanyOperatingQueryInput> inputType(){return CompanyOperatingQueryInput.class;}
    @Override public String requiredPermission(){return "menu:project";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "实时查询自持公司经营：按项目所属自持公司分组。projects项目数；contracts仅审批完成、类别1且乙方为该自持公司的施工合同最终金额；"
            +"receipts审批完成的回款登记与其他收入登记合计；invoice审批完成的开票申请主单金额，不是实际开票金额；all全部指标，须具备全部来源权限。"
            +"summary公司全量汇总；detail按项目分页，不得把一页明细当合计；companies仅公司名称与编号定位。公司名称精确匹配，同名必须确认。"
            +"项目精确定位，同名追问。日期仅项目创建日期，用于选择项目后查询这些项目的当前累计经营数据，不是业务发生期间或历史快照。日期不明确先调用companyOperating.clarifyDate。"
            +"每轮实时调用，包括第一家公司/第一条等追问，用上下文的真实companyId/projectId，不用列表序号代替。待办等仍用审批查询。"
            +"暂不支持同比环比、多组比较、公司利润、实际开票或自定义筛选，遇到这些需求应说明并追问，不得换成近似指标。";}
    @Override public String argumentsSchemaJson(){return """
        {"type":"object","properties":{
          "projectId":{"type":"string"},"projectCode":{"type":"string"},"projectName":{"type":"string","description":"精确项目名称"},
          "companyId":{"type":"string","description":"真实自持公司ID，不是列表序号"},"companyName":{"type":"string","description":"精确公司名称，同名追问"},
          "metric":{"type":"string","enum":["all","projects","contracts","receipts","invoice"]},
          "mode":{"type":"string","enum":["summary","detail","companies"]},
          "dateField":{"type":"string","enum":["projectCreateDate"]},"startDate":{"type":"string"},"endDate":{"type":"string"},
          "page":{"type":"integer"},"pageSize":{"type":"integer"}},"additionalProperties":false}
        """;}
    @Override public Object execute(CompanyOperatingQueryInput input,ToolContext context) {
        if(!context.permissions().containsAll(CompanyOperatingDomainContributor.permissions(input.metric())))
            throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号尚未开通所查询公司经营指标的查看权限，可以选择已获授权的指标单独查询");
        CompanyOperatingQueryInput resolved=input;
        if(input.projectId()!=null||input.projectCode()!=null||input.projectName()!=null) {
            var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
            if(matches.size()>1)return new ClarificationPrompt("匹配到多个项目，请确认要查看哪一个：","PROJECT_REFERENCE",matches.stream().limit(5)
                    .map(p->new ClarificationPrompt.Option("project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）",CompanyOperatingDomainContributor.CODE)).toList());
            resolved=input.withProjectId(matches.getFirst().projectId());
        }
        if(resolved.companyName()!=null && resolved.companyId()==null && !"companies".equals(resolved.mode())) {
            var candidates=client.query(context,resolved.lookup());
            if(candidates.rows().size()>1 || candidates.hasNext()==Boolean.TRUE || candidates.total()!=null && candidates.total()>1) {
                return new ClarificationPrompt("有多家同名自持公司，请根据公司编号确认：","COMPANY_REFERENCE",candidates.rows().stream().limit(5)
                        .map(r->new ClarificationPrompt.Option("company:"+r.get("companyId"),r.get("companyName")+"（编号："+r.get("companyCode")+"）",CompanyOperatingDomainContributor.CODE)).toList());
            }
            if(candidates.rows().isEmpty())return candidates;
            resolved=resolved.withCompanyId(String.valueOf(candidates.rows().getFirst().get("companyId")));
        }
        var result=client.query(context,resolved);
        var filters=new ArrayList<>(result.appliedFilters());
        if(resolved.projectId()!=null)filters.add(Map.of("field","projectId","op","eq","value",resolved.projectId()));
        if(resolved.companyId()!=null)filters.add(Map.of("field","companyId","op","eq","value",resolved.companyId()));
        return new ProjectQueryResult(result.mode(),result.page(),result.pageSize(),result.total(),result.hasNext(),result.columns(),result.rows(),filters);
    }
}
