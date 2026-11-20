package com.smart.agent.tool.operationcompare;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectOperationCompareTool implements AgentTool<ProjectOperationCompareInput, Object> {
    private final ProjectOperationCompareBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectOperationCompareTool(ProjectOperationCompareBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "projectOperation.compare"; }
    @Override public Class<ProjectOperationCompareInput> inputType() { return ProjectOperationCompareInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时比较2至5个项目的当前经营数据，以指标行、项目列展示合同额、有效目标成本、利润、利润率、回款与工期。"
                + "按用户项目顺序返回，默认首个项目为基准，必须告知用户；差额是当前项目减基准项目，比例差用百分点。"
                + "预算利润与实际收支利润分别展示，实际收支利润不是会计利润。未完工展示已施工天数，不称最终总工期。"
                + "当前仅支持CURRENT；不能用当前累计模拟期间、历史或同比。缺失及无权数据按原因说明，不补0。"
                + "每轮实时重查；增加、删除或更换基准时传完整项目列表，保留已确认的真实项目ID。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projects":{"type":"array","description":"按用户顺序列出2至5个不同项目；每项提供真实ID、精确编号或精确名称，同名先确认","items":{"type":"object","properties":{
                    "projectId":{"type":"string","description":"真实项目ID，不能填列表序号或项目编号"},
                    "projectCode":{"type":"string","description":"精确项目编号"},
                    "projectName":{"type":"string","description":"精确项目名称"}},"additionalProperties":false}},
                  "metricKeys":{"type":"array","description":"可省略以返回默认经营指标。支持projectType、projectNature、managerName、finalContractAmount、targetCostAmount、budgetProfit、budgetProfitRate、actualProfit、actualProfitRate、costRate、paymentReceivedAmount、otherIncomeAmount、totalIncomeAmount、netPaymentAmount、completedOutputAmount、reportedOutputAmount、confirmedOutputAmount、contractReceiptRate、outputReceiptRate、unreceivedAmount、contractDurationDays、planDurationDays、elapsedConstructionDays、actualDurationDays、progressPercent、actualStartDate、actualEndDate，以及基础资金分项paymentAmount、otherPaymentAmount、reimburseAmount、refundAmount、invoiceApplicationAmount。金额元，比例已乘100","items":{"type":"string"}},
                  "baselineProjectId":{"type":"string","description":"比较列表中的真实项目ID，省略时取第一个项目并明确说明"},
                  "dateMode":{"type":"string","description":"默认CURRENT当前累计；当前不支持PERIOD或AS_OF比较"},
                  "dateField":{"type":"string","description":"CURRENT禁止携带日期维度"},
                  "startDate":{"type":"string"},"endDate":{"type":"string"}
                },"required":["projects"],"additionalProperties":false}
                """;
    }
    @Override public Object execute(ProjectOperationCompareInput input, ToolContext context) {
        if (input.projects().size() < 2 || input.projects().size() > 5) throw invalid("请指定2至5个不同项目进行比较");
        List<ProjectOperationCompareInput.ProjectSelection> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < input.projects().size(); i++) {
            var item = input.projects().get(i);
            if (item.projectId() == null && item.projectCode() == null && item.projectName() == null)
                throw invalid("请补充每个项目的准确名称或编号");
            if (item.projectId() != null && !context.canAccessProject(item.projectId()))
                throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN", HttpStatus.FORBIDDEN, "所选比较项目不在当前可查看范围内");
            var matches = projects.resolve(context, item.projectId(), item.projectCode(), item.projectName());
            if (matches.size() > 1) return new ClarificationPrompt("第" + (i + 1) + "个比较项目有多个匹配，请确认准确项目编号：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(p -> new ClarificationPrompt.Option("project:" + p.projectId(),
                            p.projectName() + "（编号：" + p.projectCode() + "）", "PROJECT_OPERATION")).toList());
            var match = matches.getFirst();
            if (!seen.add(match.projectId())) throw invalid("比较列表中包含同一个项目，请选择不同项目");
            resolved.add(new ProjectOperationCompareInput.ProjectSelection(match.projectId(), match.projectCode(), match.projectName()));
        }
        if (input.baselineProjectId() != null && !seen.contains(input.baselineProjectId())) throw invalid("基准项目必须属于本次比较列表");
        return client.compare(context, new ProjectOperationCompareInput(resolved, input.metricKeys(), input.baselineProjectId(),
                input.dateMode(), input.dateField(), input.startDate(), input.endDate()));
    }
    private AgentException invalid(String message) { return new AgentException("AGENT_QUERY_VALUE_INVALID", HttpStatus.BAD_REQUEST, message); }
}
