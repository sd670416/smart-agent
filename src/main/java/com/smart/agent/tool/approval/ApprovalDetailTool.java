package com.smart.agent.tool.approval;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import com.smart.agent.common.error.AgentException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApprovalDetailTool implements AgentTool<ApprovalDetailInput, ApprovalDetailResult> {
    private final ApprovalBusinessClient client;
    public ApprovalDetailTool(ApprovalBusinessClient client) { this.client = client; }
    @Override public String key() { return "approval.getDetail"; }
    @Override public Class<ApprovalDetailInput> inputType() { return ApprovalDetailInput.class; }
    @Override public String requiredPermission() { return ""; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "查看当前用户有权查看的审批详情。processInstanceId 应使用 approval.query 返回的流程实例ID，不要把业务编号或流程名称当作ID；项目报备使用实际提交的业务表单，不是项目档案"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"processInstanceId\":{\"type\":\"string\"},"
                + "\"taskId\":{\"type\":\"string\"},\"historyId\":{\"type\":\"string\"},"
                + "\"scope\":{\"type\":\"string\"},\"visibility\":{\"type\":\"string\"},"
                + "\"personKeyword\":{\"type\":\"string\"}},\"required\":[\"processInstanceId\"],"
                + "\"additionalProperties\":false}";
    }
    @Override public ApprovalDetailResult execute(ApprovalDetailInput input, ToolContext context) {
        String id = input.processInstanceId();
        if (id == null || !id.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            id = resolveVisibleTodo(id, context);
        }
        return client.detail(context, new ApprovalDetailInput(id, input.taskId(), input.historyId(),
                input.scope(), input.visibility(), input.personKeyword()));
    }

    private String resolveVisibleTodo(String keyword, ToolContext context) {
        if (keyword == null || keyword.trim().length() < 4) throw ambiguous();
        ApprovalQueryResult result = client.query(context, new ApprovalQueryInput("TODO", "SELF", null,
                null, null, null, null, null, 1, 100, "PROCESS", null));
        if (result == null || result.total() == null || result.total() > 100 || result.items() == null)
            throw ambiguous();
        List<String> matches = result.items().stream()
                .filter(item -> item != null && item.get("fields") instanceof Map)
                .filter(item -> {
                    Map<?, ?> fields = (Map<?, ?>) item.get("fields");
                    Object name = fields.get("processName");
                    Object businessKey = fields.get("businessKey");
                    return name != null && String.valueOf(name).contains(keyword)
                            || businessKey != null && keyword.equals(String.valueOf(businessKey));
                })
                .map(item -> item.get("processInstanceId"))
                .filter(String.class::isInstance).map(String.class::cast)
                .distinct().toList();
        if (matches.size() != 1) throw ambiguous();
        return matches.getFirst();
    }

    private AgentException ambiguous() {
        return new AgentException("AGENT_APPROVAL_DETAIL_AMBIGUOUS", HttpStatus.BAD_REQUEST,
                "无法唯一定位待办，请在最新待办列表中指定序号或流程名称");
    }
}
