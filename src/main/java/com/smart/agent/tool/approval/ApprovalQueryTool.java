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
                + "\"scope\":{\"type\":\"string\",\"description\":\"scope只允许TODO、PROCESSED、STARTED，不能填写SELF或ALL；分别表示待办、已办、我发起，默认TODO\"},"
                + "\"visibility\":{\"type\":\"string\",\"description\":\"visibility只允许SELF或ALL；分别表示本人或全部，默认SELF\"},"
                + "\"personKeyword\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},"
                + "\"pageSize\":{\"type\":\"integer\"},"
                + "\"processType\":{\"type\":\"string\"},\"filter\":{\"type\":\"object\"},"
                + "\"select\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"groupBy\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"aggregations\":{\"type\":\"array\",\"description\":\"统计总数时field必须省略；alias必须是英文字母开头的英文标识符\",\"items\":{\"type\":\"object\",\"properties\":{\"function\":{\"type\":\"string\",\"description\":\"count、sum、avg、min或max\"},\"field\":{\"type\":\"string\",\"description\":\"count总数时省略，其他聚合使用字段目录中的字段名或中文标签\"},\"alias\":{\"type\":\"string\",\"description\":\"英文字母开头，仅包含英文字母、数字或下划线\"}},\"additionalProperties\":false}},"
                + "\"orderBy\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"field\":{\"type\":\"string\"},\"direction\":{\"type\":\"string\"}},\"additionalProperties\":false}},"
                + "\"recordMode\":{\"type\":\"string\",\"description\":\"PROCESS流程去重或OPERATION操作记录；默认PROCESS\"}},"
                + "\"additionalProperties\":false}";
    }
    @Override public ApprovalQueryResult execute(ApprovalQueryInput input, ToolContext context) { return client.query(context, input); }
}
