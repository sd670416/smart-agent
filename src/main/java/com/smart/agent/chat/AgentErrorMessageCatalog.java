package com.smart.agent.chat;

import java.util.Map;

final class AgentErrorMessageCatalog {
    private static final String FALLBACK = "抱歉，本次请求暂时未能完成，请稍后重试。";
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("AGENT_PROJECT_NOT_FOUND", "未找到您有权访问的匹配项目，请检查项目名称或编号。"),
            Map.entry("AGENT_PROJECT_FORBIDDEN", "您暂无权限查看该项目。"),
            Map.entry("AGENT_PROJECT_MENU_FORBIDDEN", "您当前没有项目报备或项目档案的访问权限，请联系管理员授权。"),
            Map.entry("AGENT_PROJECT_ARCHIVE_TOO_LARGE", "该项目档案数据量较大，请指定要查看的部分。"),
            Map.entry("AGENT_TOOL_PROJECT_FORBIDDEN", "您暂无权限查看该项目。"),
            Map.entry("AGENT_TOOL_FORBIDDEN", "您暂无权限执行本次查询。"),
            Map.entry("AGENT_TOOL_INVALID_INPUT", "暂时无法识别本次查询条件，请换一种说法后重试。"),
            Map.entry("AGENT_TOOL_NOT_FOUND", "暂不支持这项查询，请尝试换一种提问方式。"),
            Map.entry("AGENT_TOOL_TIMEOUT", "业务数据查询超时，请稍后重试。"),
            Map.entry("AGENT_TOOL_EXECUTION_FAILED", "业务数据查询失败，请稍后重试。"),
            Map.entry("AGENT_TOOL_INTERRUPTED", "业务数据查询已中断，请重新尝试。"),
            Map.entry("AGENT_TOOL_RESULT_TOO_LARGE", "查询结果内容过多，请缩小查询范围后重试。"),
            Map.entry("AGENT_TOOL_CALL_LIMIT", "本次查询步骤过多，请缩小查询范围后重试。"),
            Map.entry("AGENT_QUERY_FIELD_UNKNOWN", "暂未支持您指定的项目字段，请换一个项目业务字段再试。"),
            Map.entry("AGENT_QUERY_OPERATOR_INVALID", "暂不支持当前筛选方式，请换一种筛选条件表达。"),
            Map.entry("AGENT_QUERY_VALUE_INVALID", "筛选条件的取值格式不正确，请检查后重试。"),
            Map.entry("AGENT_QUERY_TOO_COMPLEX", "查询条件过于复杂，请减少筛选、分组或统计条件。"),
            Map.entry("AGENT_QUERY_INVALID_REQUEST", "我已识别到这是项目统计查询，但查询条件格式不完整。请明确统计方式，例如“按项目状态分组统计数量”，或补充具体时间范围。"),
            Map.entry("AGENT_QUERY_UNAVAILABLE", "当前项目查询能力暂时不可用。你可以先说明项目名称、状态、类型或时间范围，我会按支持的条件重新查询。"),
            Map.entry("AGENT_TIMEZONE_INVALID", "无法识别指定时区，请使用例如 Asia/Shanghai 的标准时区名称。"),
            Map.entry("AGENT_WEB_SEARCH_DISABLED", "当前未启用联网搜索，请联系管理员配置。"),
            Map.entry("AGENT_WEB_SEARCH_FORBIDDEN", "您暂无使用联网搜索的权限。"),
            Map.entry("AGENT_WEB_SEARCH_INVALID_INPUT", "无法识别联网查询条件，请补充明确的公开信息关键词。"),
            Map.entry("AGENT_WEB_SEARCH_SENSITIVE_INPUT", "该问题涉及内部业务信息，不能发送到公网。我可以改用系统业务查询。"),
            Map.entry("AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED", "当前模型不支持联网搜索，请切换支持联网能力的模型。"),
            Map.entry("AGENT_WEB_SEARCH_RATE_LIMITED", "当前联网查询请求较多，请稍后重试。"),
            Map.entry("AGENT_WEB_SEARCH_TIMEOUT", "联网查询超时，请稍后重试。"),
            Map.entry("AGENT_WEB_SEARCH_FAILED", "联网查询失败，请稍后重试或补充更明确的关键词。"),
            Map.entry("AGENT_WEB_SEARCH_NO_RESULTS", "未找到可靠的公开来源，请补充关键词或限定时间范围。"),
            Map.entry("AGENT_MODEL_TIMEOUT", "模型响应超时，请稍后重试。"),
            Map.entry("AGENT_RUN_TIMEOUT", "本次请求处理超时，请稍后重试。"),
            Map.entry("AGENT_MODEL_FAILED", "模型服务暂时不可用，请稍后重试。"),
            Map.entry("AGENT_MODEL_DISABLED", "当前会话使用的模型已停用，请在右侧切换到其他可用模型后重试。"),
            Map.entry("AGENT_MODEL_NOT_FOUND", "当前会话使用的模型已被删除，请在右侧重新选择模型后重试。"),
            Map.entry("AGENT_MODEL_NOT_CONFIGURED", "系统尚未配置可用的默认模型，请联系管理员在模型配置中心设置后重试。"),
            Map.entry("AGENT_MODEL_NOT_SELECTED", "请先选择本次对话要使用的模型，再发送消息。"),
            Map.entry("AGENT_MODEL_CAPABILITY_MISSING", "所选模型不具备完成本次请求所需的能力，请切换到能力匹配的模型后重试。"),
            Map.entry("AGENT_MODEL_VISION_REQUIRED", "所选模型暂不支持图片分析，请切换到支持视觉能力的模型，或改用文字提问。"),
            Map.entry("AGENT_MODEL_WEB_SEARCH_REQUIRED", "所选模型暂不支持联网搜索，请切换到支持联网能力的模型后重试。"),
            Map.entry("AGENT_MODEL_SWITCH_FORBIDDEN", "该模型不可用于当前会话，请刷新后重新选择。"),
            Map.entry("AGENT_MODEL_PROTOCOL_ERROR", "模型响应格式异常，请稍后重试。"),
            Map.entry("AGENT_PROJECT_DATA_NOT_REFRESHED", "未能获取最新项目数据，请稍后重试。"),
            Map.entry("AGENT_APPROVAL_PERMISSION_DENIED", "您当前没有任务中心的访问权限，请联系管理员授权。"),
            Map.entry("AGENT_APPROVAL_FIELD_UNKNOWN", "暂未识别该审批字段，请补充具体字段或选择系统支持的字段。"),
            Map.entry("AGENT_APPROVAL_OPERATOR_INVALID", "暂不支持当前审批筛选方式，请换一种筛选条件表达。"),
            Map.entry("AGENT_APPROVAL_VALUE_INVALID", "审批筛选条件的取值格式不正确，请检查后重试。"),
            Map.entry("AGENT_APPROVAL_QUERY_TOO_COMPLEX", "审批查询条件过于复杂，请减少筛选、分组或统计条件。"),
            Map.entry("AGENT_APPROVAL_PERSON_AMBIGUOUS", "匹配到多位同名人员，请补充部门或完整姓名后重试。"),
            Map.entry("AGENT_APPROVAL_PERSON_NOT_FOUND", "未找到匹配的人员，请检查姓名或账号后重试。"),
            Map.entry("AGENT_APPROVAL_DETAIL_NOT_ACCESSIBLE", "该审批记录不存在或您当前无权查看，请从最新审批列表中重新选择。"),
            Map.entry("AGENT_APPROVAL_DETAIL_AMBIGUOUS", "无法唯一定位这条待办，请指定最新待办列表中的序号或完整流程名称。"),
            Map.entry("AGENT_APPROVAL_CONTEXT_MISSING", "当前会话中没有可用的审批列表，请先查询待办、已办或我发起的流程，再指定第几条。"),
            Map.entry("AGENT_APPROVAL_ORDINAL_OUT_OF_RANGE", "指定的序号不在最新审批列表中，请选择列表中已有的序号。"),
            Map.entry("AGENT_APPROVAL_CONTEXT_INVALID", "最新审批列表缺少详情定位信息，请重新查询列表后再选择。"),
            Map.entry("AGENT_APPROVAL_QUERY_FAILED", "审批数据查询失败，请稍后重试。"),
            Map.entry("AGENT_APPROVAL_DATA_NOT_REFRESHED", "未能获取最新审批数据，请稍后重试。"),
            Map.entry("AGENT_APPROVAL_QUERY_UNAVAILABLE", "当前审批查询能力暂时不可用，请稍后重试查询待办、已办或我发起。"),
            Map.entry("AGENT_APPROVAL_INVALID_REQUEST", "审批查询条件格式不完整，请明确查询待办、已办或我发起，并补充需要的筛选条件。"),
            Map.entry("AGENT_MODEL_EMPTY_RESPONSE", "模型未返回有效内容，请重新提问。"),
            Map.entry("AGENT_MODEL_TURN_LIMIT", "本次查询步骤过多，请缩小查询范围后重试。"),
            Map.entry("AGENT_ANSWER_TOO_LARGE", "回答内容过长，请缩小查询范围后重试。"),
            Map.entry("AGENT_ATTACHMENT_NOT_READY", "附件尚未上传完成，请稍后再发送。"),
            Map.entry("AGENT_ATTACHMENTS_TOO_MANY", "本次上传的附件数量过多，请减少附件后重试。"),
            Map.entry("AGENT_PERSISTENCE_FAILED", "消息保存失败，请稍后重试。"),
            Map.entry("AGENT_UNAUTHORIZED", "登录状态已失效，请重新登录后再试。"),
            Map.entry("AGENT_CHAT_FAILED", FALLBACK));

    private AgentErrorMessageCatalog() {
    }

    static String message(String code, boolean hasAttachments) {
        if ("AGENT_MODEL_FAILED".equals(code) && hasAttachments) {
            return "当前模型暂不支持图片分析，请改用文字提问或切换支持视觉能力的模型。";
        }
        return MESSAGES.getOrDefault(code, FALLBACK);
    }

    static String queryClarification(String code, String detail) {
        String prefix = switch (code) {
            case "AGENT_QUERY_FIELD_UNKNOWN" -> "我暂时无法识别你要查询的项目字段。";
            case "AGENT_QUERY_OPERATOR_INVALID" -> "我暂时无法按这种方式筛选该项目字段。";
            default -> "我暂时无法确定这个查询条件的取值。";
        };
        return prefix + "\n" + detail + "\n请补充更明确的字段或条件后重试。可查询项目名称、项目编号、项目状态、项目类型、建设单位和创建时间等业务信息。";
    }
}
