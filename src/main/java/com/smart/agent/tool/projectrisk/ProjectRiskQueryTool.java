package com.smart.agent.tool.projectrisk;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class ProjectRiskQueryTool implements AgentTool<ProjectRiskQueryInput,Object> {
    private final ProjectRiskBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectRiskQueryTool(ProjectRiskBusinessClient client,ProjectReferenceResolver projects){this.client=client;this.projects=projects;}
    @Override public String key(){return "projectRisk.query";}
    @Override public Class<ProjectRiskQueryInput> inputType(){return ProjectRiskQueryInput.class;}
    @Override public String requiredPermission(){return "";}
    @Override public ToolRisk risk(){return ToolRisk.L1;}
    @Override public String description(){return "实时查询可见项目经营风险及工期。已接入成本、付款、回款、开票、收票、进度、质量、安全八维。siteFacts展示工期；detail风险明细，summary全量汇总，evidence展示重新评估的事实金额、阈值及单据依据，不是完整表单。无权限、缺来源或缺到期节点的规则说明未评估，已知风险和未评估可同时存在。回款不含其他收入；开票是审批完成申请，付款仅未付压力不认定到期拖欠，收票缺口不认定税务违规。仅当前状态，不重建历史。";}
    @Override public String argumentsSchemaJson(){return """
        {"type":"object","properties":{
          "projectId":{"type":"string","description":"真实项目ID"},"projectCode":{"type":"string","description":"精确项目编号"},"projectName":{"type":"string","description":"精确名称，同名先确认"},
          "dimensions":{"type":"array","items":{"type":"string"},"description":"COST成本、PAYMENT付款、RECEIVABLE回款、INVOICE开票、SUPPLIER_INVOICE收票、PROGRESS进度、QUALITY质量、SAFETY安全；省略查全部八维，明确问某类时仅选对应维度"},
          "level":{"type":"string","description":"HIGH高、MEDIUM中、LOW低、NONE未触发；省略全部含未评估"},
          "mode":{"type":"string","description":"detail明细默认，summary全量汇总，evidence评估依据，siteFacts工期事实"},
          "dateMode":{"type":"string","description":"仅CURRENT"},"dateField":{"type":"string","description":"暂不支持历史日期"},"startDate":{"type":"string"},"endDate":{"type":"string"},
          "page":{"type":"integer","description":"1开始"},"pageSize":{"type":"integer","description":"1至100，默认20"}
        },"additionalProperties":false}
        """;}
    @Override public Object execute(ProjectRiskQueryInput input,ToolContext context){
        if(input.projectId()==null && input.projectCode()==null && input.projectName()==null)return client.query(context,input);
        var matches=projects.resolve(context,input.projectId(),input.projectCode(),input.projectName());
        if(matches.size()>1)return new ClarificationPrompt("请确认要查询哪个项目的风险或工期：","PROJECT_REFERENCE",matches.stream().limit(5).map(p->new ClarificationPrompt.Option("project:"+p.projectId(),p.projectName()+"（编号："+p.projectCode()+"）","PROJECT_RISK")).toList());
        return client.query(context,input.withProjectId(matches.getFirst().projectId()));
    }
}
