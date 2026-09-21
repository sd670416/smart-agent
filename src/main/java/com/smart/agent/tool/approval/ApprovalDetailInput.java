package com.smart.agent.tool.approval;

public record ApprovalDetailInput(String processInstanceId, String taskId, String historyId,
                                  String scope, String visibility, String personKeyword) {
}
