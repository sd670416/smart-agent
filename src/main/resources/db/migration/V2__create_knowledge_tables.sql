CREATE TABLE ai_knowledge_space (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    organization_id varchar(36),
    name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    updated_by varchar(36) NOT NULL,
    update_time datetime(3) NOT NULL,
    deleted bit(1) NOT NULL DEFAULT b'0',
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_knowledge_space_id_tenant (id, tenant_id),
    KEY idx_ai_knowledge_space_tenant_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_document (
    id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    space_id varchar(36) NOT NULL,
    organization_id varchar(36),
    project_id varchar(36),
    title varchar(255) NOT NULL,
    source_type varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    updated_by varchar(36) NOT NULL,
    update_time datetime(3) NOT NULL,
    deleted bit(1) NOT NULL DEFAULT b'0',
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_document_id_tenant (id, tenant_id),
    KEY idx_ai_document_scope_status (tenant_id, space_id, project_id, status, deleted),
    CONSTRAINT fk_ai_document_space FOREIGN KEY (space_id, tenant_id)
        REFERENCES ai_knowledge_space (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_document_version (
    id varchar(36) NOT NULL,
    document_id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    version_no bigint NOT NULL,
    source_text longtext NOT NULL,
    source_checksum char(64) NOT NULL,
    parser_version varchar(64) NOT NULL,
    embedding_model_key varchar(128) NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    updated_by varchar(36) NOT NULL,
    update_time datetime(3) NOT NULL,
    deleted bit(1) NOT NULL DEFAULT b'0',
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_document_version_identity (id, document_id, tenant_id),
    UNIQUE KEY uq_ai_document_version_checksum (document_id, source_checksum),
    CONSTRAINT fk_ai_document_version_document FOREIGN KEY (document_id, tenant_id)
        REFERENCES ai_document (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_document_acl (
    id varchar(36) NOT NULL,
    document_id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    subject_type varchar(32) NOT NULL,
    subject_id varchar(36) NOT NULL,
    permission varchar(32) NOT NULL,
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    updated_by varchar(36) NOT NULL,
    update_time datetime(3) NOT NULL,
    deleted bit(1) NOT NULL DEFAULT b'0',
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_document_acl_subject (document_id, subject_type, subject_id, permission),
    KEY idx_ai_document_acl_lookup (tenant_id, subject_type, subject_id, permission, deleted),
    CONSTRAINT fk_ai_document_acl_document FOREIGN KEY (document_id, tenant_id)
        REFERENCES ai_document (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_document_chunk (
    id varchar(36) NOT NULL,
    document_id varchar(36) NOT NULL,
    document_version_id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    ordinal_no int NOT NULL,
    content longtext NOT NULL,
    content_checksum char(64) NOT NULL,
    vector_point_id varchar(36) NOT NULL,
    page_number int,
    section_title varchar(255),
    status varchar(32) NOT NULL,
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    updated_by varchar(36) NOT NULL,
    update_time datetime(3) NOT NULL,
    deleted bit(1) NOT NULL DEFAULT b'0',
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_document_chunk_ordinal (document_version_id, ordinal_no),
    UNIQUE KEY uq_ai_document_chunk_point (vector_point_id),
    KEY idx_ai_document_chunk_document (tenant_id, document_id, status, deleted),
    CONSTRAINT fk_ai_document_chunk_version FOREIGN KEY (document_version_id, document_id, tenant_id)
        REFERENCES ai_document_version (id, document_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_ingestion_job (
    id varchar(36) NOT NULL,
    document_id varchar(36) NOT NULL,
    document_version_id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count int NOT NULL DEFAULT 1,
    failure_code varchar(128),
    started_time datetime(3) NOT NULL,
    finished_time datetime(3),
    created_by varchar(36) NOT NULL,
    create_time datetime(3) NOT NULL,
    updated_by varchar(36) NOT NULL,
    update_time datetime(3) NOT NULL,
    deleted bit(1) NOT NULL DEFAULT b'0',
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_ingestion_job_status (tenant_id, status, update_time),
    CONSTRAINT fk_ai_ingestion_job_version FOREIGN KEY (document_version_id, document_id, tenant_id)
        REFERENCES ai_document_version (id, document_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
