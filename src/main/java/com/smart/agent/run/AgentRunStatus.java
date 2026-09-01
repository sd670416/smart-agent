package com.smart.agent.run;

public enum AgentRunStatus {
    RECEIVED,
    ROUTING,
    PLANNING,
    TOOL_SELECTING,
    TOOL_EXECUTING,
    RETRIEVING,
    GENERATING,
    WAITING_APPROVAL,
    RESUMING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT,
    PERMISSION_DENIED
}
