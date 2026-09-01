CREATE TABLE ai_conversation (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    title varchar(255) NOT NULL,
    create_time datetime(3) NOT NULL,
    update_time datetime(3) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_conversation_tenant_user_update (tenant_id, user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_message (
    id varchar(36) NOT NULL,
    conversation_id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    role varchar(32) NOT NULL,
    content LONGTEXT NOT NULL,
    sequence_no bigint NOT NULL,
    create_time datetime(3) NOT NULL,
    update_time datetime(3) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_message_conversation_sequence (conversation_id, sequence_no),
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_run (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    conversation_id varchar(36) NOT NULL,
    status varchar(32) NOT NULL,
    create_time datetime(3) NOT NULL,
    update_time datetime(3) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_run_tenant_user_update (tenant_id, user_id, update_time),
    KEY idx_ai_run_conversation (conversation_id),
    CONSTRAINT fk_ai_run_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_run_step (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    run_id varchar(36) NOT NULL,
    step_no bigint NOT NULL,
    step_type varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    safe_input_summary LONGTEXT,
    safe_output_summary LONGTEXT,
    started_time datetime(3),
    finished_time datetime(3),
    create_time datetime(3) NOT NULL,
    update_time datetime(3) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_run_step_run_sequence (run_id, step_no),
    CONSTRAINT fk_ai_run_step_run FOREIGN KEY (run_id) REFERENCES ai_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
