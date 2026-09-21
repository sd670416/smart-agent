package com.smart.agent.tool.approval;

import java.util.List;
import java.util.Map;

public record ApprovalQueryResult(Integer page, Integer pageSize, Long total,
                                  List<Map<String, Object>> items,
                                  Map<String, Object> summary,
                                  List<Map<String, Object>> groups) {
}
