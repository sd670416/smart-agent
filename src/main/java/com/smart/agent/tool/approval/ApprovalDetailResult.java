package com.smart.agent.tool.approval;

import java.util.List;
import java.util.Map;

public record ApprovalDetailResult(String processInstanceId, Map<String, Object> overview,
                                   Map<String, Object> businessForm,
                                   List<Map<String, Object>> currentStatus,
                                   List<Map<String, Object>> history,
                                   Map<String, Object> attachments) {
}
