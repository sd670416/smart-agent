package com.smart.agent.tool.material;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.MaterialPriceDomainContributor;
import org.springframework.stereotype.Component;

@Component
public class MaterialPriceQueryTool implements AgentTool<MaterialPriceQueryInput, Object> {
    private final MaterialPriceBusinessClient client;
    private final ProjectReferenceResolver projects;
    public MaterialPriceQueryTool(MaterialPriceBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "materialPrice.query"; }
    @Override public Class<MaterialPriceQueryInput> inputType() { return MaterialPriceQueryInput.class; }
    @Override public String requiredPermission() { return MaterialPriceDomainContributor.PERMISSION; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时查询某种材料的历史采购价格全部明细，来自可见项目审批完成的材料合同（预算内与预算外），不含公司备库采购、申请预算价或供应商报价。"
                + "materialName材料名称包含匹配，保留每次合同材料行，不聚合、不去重。采购时间=合同签订日期，价格=合同含税单价（元），不是金额。"
                + "支持材料规格、单位、项目、签订日期范围。按时间用signDate、按价格用unitPrice；asc从低到高/从早到晚，desc相反，默认时间升序。"
                + "后端对全部匹配明细排序后分页，total是全部明细条数，不能只给当前页排序或把当前页称为全部记录。"
                + "不同规格、单位不换算、不推断同质可比；显示规格和单位，价格或签订日期为空显示未填写，不能当作0。"
                + "材料名称有歧义或用户只想某种规格但未说明时先追问；明确要求所有规格则全部展示。"
                + "追问排序和翻页必须保留材料、项目、规格、单位、日期并重新调用本工具，改变排序回第1页；相对日期先取当前时间。"
                + "结果用中文业务列名表格展示，不向用户输出技术字段；历史上下文只定位条件，不能当作当前数据。";
    }
    @Override public String argumentsSchemaJson() {
        return """
            {"type":"object","properties":{
              "materialName":{"type":"string","description":"必填，材料名称包含匹配；只填材料名，不含规格、排序等指令。不明确先追问"},
              "specification":{"type":"string","description":"可选，精确规格。不填写表示所有规格，不得擅自补充"},
              "unit":{"type":"string","description":"可选，精确单位"},
              "projectId":{"type":"string","description":"真实项目ID，不是项目编号"},
              "projectCode":{"type":"string","description":"精确项目编号，由程序解析"},
              "projectName":{"type":"string","description":"精确项目名称，同名追问"},
              "startDate":{"type":"string","description":"合同签订开始日期yyyy-MM-dd，含当天，与endDate一起提供"},
              "endDate":{"type":"string","description":"合同签订结束日期yyyy-MM-dd，含当天"},
              "sortBy":{"type":"string","description":"只允许signDate或unitPrice：时间为signDate，含税单价为unitPrice"},
              "sortDirection":{"type":"string","description":"只允许asc或desc，默认asc升序"},
              "page":{"type":"integer","description":"最小1，默认1，改变排序回第一页"},
              "pageSize":{"type":"integer","description":"1到100，默认20，通过翻页查看全部记录"}
            },"required":["materialName"],"additionalProperties":false}
            """;
    }
    @Override public Object execute(MaterialPriceQueryInput input, ToolContext context) {
        if (input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projects.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) return new ClarificationPrompt("匹配到多个项目，请确认要查看哪个项目的材料采购价格：",
                "PROJECT_REFERENCE", matches.stream().limit(5).map(p -> new ClarificationPrompt.Option("project:" + p.projectId(),
                        p.projectName() + "（编号：" + p.projectCode() + "）", MaterialPriceDomainContributor.CODE)).toList());
        String id = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(id));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", id));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
