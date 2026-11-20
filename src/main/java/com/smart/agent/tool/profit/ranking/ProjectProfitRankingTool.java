package com.smart.agent.tool.profit.ranking;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectProfitRankingTool implements AgentTool<ProjectProfitRankingInput, Object> {
    private final ProjectProfitRankingBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectProfitRankingTool(ProjectProfitRankingBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "projectProfit.rank"; }
    @Override public Class<ProjectProfitRankingInput> inputType() { return ProjectProfitRankingInput.class; }
    @Override public String requiredPermission() { return null; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时查询可见项目的盈利排名；默认预算利润率从高到低前20个，必须向用户说明排序指标、方向和数量。"
                + "预算利润=施工合同最终金额减审批完成有效目标成本；实际收支利润=总收入减净付款，不是会计利润。"
                + "支持四种利润金额/率指标、亏损项目筛选、精确项目/组织/经理/类型/性质筛选。后端全量排序后分页，同值同名次，后续跳号；缺来源不补0，缺预算不算100%利润率。"
                + "仅支持当前累计，日期问题不明确时追问，不能把今年创建项目当今年利润，不能拿当前累计做同比或历史排名。"
                + "必须读取appliedFilters中的rankingDescription及不可排名数量和原因；total是TopN截取后的条数，不是全量项目数。"
                + "比例字段已经乘100，不得再次乘100；项目ID用于定位不作为客户展示列；下一页和改指标必须重新调用。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，只能来自可信项目结果"},
                  "projectCode":{"type":"string","description":"精确项目编号"},
                  "projectName":{"type":"string","description":"精确名称，同名先追问；全项目排名省略"},
                  "organizationId":{"type":"string","description":"真实所属组织ID，不填名称"},
                  "projectNature":{"type":"string","description":"项目性质中文标签或有效字典值"},
                  "projectType":{"type":"string","description":"项目类型中文标签或有效字典值"},
                  "managerId":{"type":"string","description":"真实项目经理ID，不填姓名；不明确时先确认"},
                  "metric":{"type":"string","description":"budgetProfitRate预算利润率（默认）、budgetProfit预算利润金额、actualProfitRate实际收支利润率、actualProfit实际收支利润金额"},
                  "direction":{"type":"string","description":"desc从高到低（默认），asc从低到高"},
                  "topN":{"type":"integer","description":"榜单最多项目数，1至100，默认20；不是每页条数"},
                  "page":{"type":"integer","description":"从1开始，保持原条件只改变页码"},
                  "pageSize":{"type":"integer","description":"每页1至100，默认20"},
                  "lossOnly":{"type":"boolean","description":"仅看对应预算利润或实际收支利润金额为负的项目"},
                  "dateMode":{"type":"string","description":"当前仅支持CURRENT，不提供期间或历史排名"},
                  "dateField":{"type":"string","description":"暂不支持，不能使用项目创建日期替代利润发生日期"},
                  "startDate":{"type":"string","description":"暂不支持，用户确认当前累计后省略"},
                  "endDate":{"type":"string","description":"暂不支持，用户确认当前累计后省略"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(ProjectProfitRankingInput input, ToolContext context) {
        if (input == null) throw new AgentException("AGENT_QUERY_VALUE_INVALID", HttpStatus.BAD_REQUEST, "请提供排名条件");
        if (input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        if (input.projectId() != null && !context.canAccessProject(input.projectId()))
            throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN", HttpStatus.FORBIDDEN, "该项目不在当前可查询范围内");
        var matches = projects.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) return new ClarificationPrompt("匹配到多个项目，请确认要查看哪一个项目的盈利排名：",
                "PROJECT_REFERENCE", matches.stream().limit(5).map(p -> new ClarificationPrompt.Option(
                        "project:" + p.projectId(), p.projectName() + "（编号：" + p.projectCode() + "）", "PROJECT_PROFIT")).toList());
        return client.query(context, input.withProjectId(matches.getFirst().projectId()));
    }
}
