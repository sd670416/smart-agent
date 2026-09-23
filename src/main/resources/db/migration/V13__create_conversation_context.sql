CREATE TABLE ai_conversation_context (
    id varchar(36) NOT NULL COMMENT '上下文ID',
    conversation_id varchar(36) NOT NULL COMMENT '会话ID',
    tenant_id varchar(36) NOT NULL COMMENT '租户ID',
    user_id varchar(36) NOT NULL COMMENT '用户ID',
    context_type varchar(64) NOT NULL COMMENT '上下文类型',
    payload_json LONGTEXT NOT NULL COMMENT '结构化上下文JSON',
    source_run_id varchar(36) NULL COMMENT '产生上下文的运行ID',
    create_time datetime(3) NOT NULL COMMENT '创建时间',
    update_time datetime(3) NOT NULL COMMENT '更新时间',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_conversation_context_scope
        (conversation_id, tenant_id, user_id, context_type),
    KEY idx_ai_conversation_context_scope_update
        (tenant_id, user_id, conversation_id, update_time),
    CONSTRAINT fk_ai_conversation_context_conversation
        FOREIGN KEY (conversation_id, tenant_id, user_id)
        REFERENCES ai_conversation (id, tenant_id, user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI会话结构化上下文';
