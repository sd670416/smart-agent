CREATE TABLE ai_pending_clarification (
    id varchar(36) NOT NULL COMMENT '澄清ID',
    tenant_id varchar(36) NOT NULL COMMENT '租户ID',
    user_id varchar(36) NOT NULL COMMENT '用户ID',
    conversation_id varchar(36) NOT NULL COMMENT '会话ID',
    source_domain varchar(64) NULL COMMENT '产生歧义时的业务域',
    intent_json LONGTEXT NOT NULL COMMENT '待确认的结构化意图JSON',
    options_json LONGTEXT NOT NULL COMMENT '真实候选选项JSON',
    status varchar(16) NOT NULL COMMENT 'PENDING/RESOLVED/EXPIRED/CANCELLED',
    selected_option_id varchar(128) NULL COMMENT '已选择的稳定选项ID',
    expires_at datetime(3) NOT NULL COMMENT '过期时间',
    resolved_at datetime(3) NULL COMMENT '确认完成时间',
    create_time datetime(3) NOT NULL COMMENT '创建时间',
    update_time datetime(3) NOT NULL COMMENT '更新时间',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    pending_slot tinyint GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED COMMENT '同一会话有效待确认项唯一槽位',
    PRIMARY KEY (id),
    CONSTRAINT ck_ai_pending_clarification_status
        CHECK (status IN ('PENDING', 'RESOLVED', 'EXPIRED', 'CANCELLED')),
    UNIQUE KEY uq_ai_pending_clarification_active
        (tenant_id, user_id, conversation_id, pending_slot),
    KEY idx_ai_pending_clarification_expiry (status, expires_at),
    KEY idx_ai_pending_clarification_scope_time
        (tenant_id, user_id, conversation_id, create_time),
    CONSTRAINT fk_ai_pending_clarification_conversation
        FOREIGN KEY (conversation_id, tenant_id, user_id)
        REFERENCES ai_conversation (id, tenant_id, user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI会话待确认意图';
