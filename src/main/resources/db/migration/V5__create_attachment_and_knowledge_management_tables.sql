CREATE TABLE ai_attachment (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    purpose varchar(32) NOT NULL,
    original_filename varchar(512) NOT NULL,
    object_key varchar(512) NOT NULL,
    size_bytes bigint,
    etag varchar(255),
    detected_media_type varchar(255),
    status varchar(32) NOT NULL,
    failure_code varchar(128),
    upload_expires_at datetime(3) NOT NULL,
    last_used_at datetime(3),
    create_time datetime(3) NOT NULL,
    update_time datetime(3) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_attachment_tenant_object (tenant_id, object_key),
    KEY idx_ai_attachment_tenant_user_status (tenant_id, user_id, status, update_time),
    KEY idx_ai_attachment_tenant_expiry (tenant_id, purpose, status, upload_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_role_knowledge_grant (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    role_id varchar(64) NOT NULL,
    knowledge_space_id varchar(36) NOT NULL,
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_role_knowledge_grant (tenant_id, role_id, knowledge_space_id),
    KEY idx_ai_role_grant_tenant_space (tenant_id, knowledge_space_id),
    CONSTRAINT fk_ai_role_grant_space FOREIGN KEY (knowledge_space_id, tenant_id)
        REFERENCES ai_knowledge_space (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_citation (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    run_id varchar(36) NOT NULL,
    message_id varchar(36),
    document_id varchar(36) NOT NULL,
    document_version_id varchar(36) NOT NULL,
    chunk_id varchar(36) NOT NULL,
    ordinal_no int NOT NULL,
    quoted_text longtext NOT NULL,
    page_number int,
    section_title varchar(255),
    create_time datetime(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_citation_run_ordinal (tenant_id, run_id, ordinal_no),
    KEY idx_ai_citation_tenant_user_run (tenant_id, user_id, run_id),
    CONSTRAINT fk_ai_citation_run FOREIGN KEY (run_id, tenant_id)
        REFERENCES ai_run (id, tenant_id),
    CONSTRAINT fk_ai_citation_chunk FOREIGN KEY (chunk_id)
        REFERENCES ai_document_chunk (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_audit_log (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    actor_user_id varchar(36) NOT NULL,
    action varchar(128) NOT NULL,
    object_type varchar(64) NOT NULL,
    object_id varchar(128),
    outcome varchar(32) NOT NULL,
    trace_id varchar(128),
    safe_summary longtext,
    create_time datetime(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_audit_tenant_time (tenant_id, create_time),
    KEY idx_ai_audit_tenant_object (tenant_id, object_type, object_id),
    KEY idx_ai_audit_tenant_trace (tenant_id, trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
