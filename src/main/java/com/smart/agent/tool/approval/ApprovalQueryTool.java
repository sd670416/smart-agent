package com.smart.agent.tool.approval;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class ApprovalQueryTool implements AgentTool<ApprovalQueryInput, ApprovalQueryResult> {
    private final ApprovalBusinessClient client;
    public ApprovalQueryTool(ApprovalBusinessClient client) { this.client = client; }
    @Override public String key() { return "approval.query"; }
    @Override public Class<ApprovalQueryInput> inputType() { return ApprovalQueryInput.class; }
    @Override public String requiredPermission() { return ""; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "查询当前用户有权查看的待办、已办或我发起审批，支持筛选、分页、统计和详情前的流程定位"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"scope\":{\"type\":\"string\",\"description\":\"TODO待办、PROCESSED已办、STARTED我发起\"},"
                + "\"visibility\":{\"type\":\"string\",\"description\":\"SELF本人或ALL全部；默认SELF\"},"
                + "\"personKeyword\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},"
                + "\"pageSize\":{\"type\":\"integer\"},"
                + "\"processType\":{\"type\":\"string\"},\"filter\":{\"type\":\"object\"},"
                + "\"select\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"groupBy\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"aggregations\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"function\":{\"type\":\"string\"},\"field\":{\"type\":\"string\"},\"alias\":{\"type\":\"string\"}},\"additionalProperties\":false}},"
                + "\"orderBy\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"field\":{\"type\":\"string\"},\"direction\":{\"type\":\"string\"}},\"additionalProperties\":false}},"
                + "\"recordMode\":{\"type\":\"string\",\"description\":\"PROCESS流程去重或OPERATION操作记录；默认PROCESS\"}},"
                + "\"additionalProperties\":false}";
    }
    @Override public ApprovalQueryResult execute(ApprovalQueryInput input, ToolContext context) { return client.query(context, input); }
}
