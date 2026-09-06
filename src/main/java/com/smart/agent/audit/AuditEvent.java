package com.smart.agent.audit;
public record AuditEvent(String tenantId,String userId,String objectId,String operation,String result,String traceId,String summary) {}
