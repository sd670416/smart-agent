package com.smart.agent.model.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_model_call_log")
public class ModelCallLog {
    @Id @Column(length = 36) private String id;
    @Column(name="tenant_id",nullable=false,length=36) private String tenantId;
    @Column(name="user_id",nullable=false,length=36) private String userId;
    @Column(name="run_id",nullable=false,length=36) private String runId;
    @Column(name="trace_id",length=128) private String traceId;
    @Column(name="model_name",nullable=false,length=128) private String modelName;
    @Column(name="provider_url",nullable=false,length=512) private String providerUrl;
    @Column(nullable=false,length=32) private String status;
    @Column(name="http_status") private Integer httpStatus;
    @Column(name="duration_millis") private Long durationMillis;
    @Column(name="input_tokens",nullable=false) private int inputTokens;
    @Column(name="output_tokens",nullable=false) private int outputTokens;
    @Column(name="safe_request_summary",columnDefinition="LONGTEXT") private String safeRequestSummary;
    @Column(name="safe_response_summary",columnDefinition="LONGTEXT") private String safeResponseSummary;
    @Column(name="error_type",length=128) private String errorType;
    @Column(name="safe_error_detail",length=2000) private String safeErrorDetail;
    @Column(name="create_time",nullable=false) private Instant createdAt;
    @Column(name="update_time",nullable=false) private Instant updatedAt;
    protected ModelCallLog() {}
    ModelCallLog(String tenantId,String userId,String runId,String traceId,String modelName,String providerUrl,String summary){
        id=UUID.randomUUID().toString(); this.tenantId=tenantId;this.userId=userId;this.runId=runId;this.traceId=traceId;
        this.modelName=modelName;this.providerUrl=providerUrl;safeRequestSummary=summary;status="STARTED";createdAt=Instant.now();updatedAt=createdAt;
    }
    void succeed(long duration,int input,int output,String summary){status="SUCCEEDED";durationMillis=duration;inputTokens=input;outputTokens=output;safeResponseSummary=summary;updatedAt=Instant.now();}
    void fail(long duration,String type,String detail){status="FAILED";durationMillis=duration;errorType=type;safeErrorDetail=detail==null?null:detail.substring(0,Math.min(2000,detail.length()));updatedAt=Instant.now();}
    public String id(){return id;}
}
