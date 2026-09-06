ALTER TABLE ai_knowledge_space
    ADD COLUMN description varchar(1000) NULL AFTER name,
    ADD COLUMN scope varchar(32) NOT NULL DEFAULT 'TENANT' AFTER description,
    ADD COLUMN project_id varchar(36) NULL AFTER scope,
    ADD KEY idx_ai_knowledge_space_scope (tenant_id, scope, project_id, status, deleted);

ALTER TABLE ai_document
    ADD COLUMN active_version_id varchar(36) NULL AFTER status;

ALTER TABLE ai_document_version
    ADD COLUMN attachment_id varchar(36) NULL AFTER tenant_id,
    MODIFY COLUMN source_text longtext NULL,
    MODIFY COLUMN source_checksum char(64) NULL,
    MODIFY COLUMN parser_version varchar(64) NULL,
    MODIFY COLUMN embedding_model_key varchar(128) NULL,
    ADD COLUMN failure_code varchar(128) NULL AFTER status,
    ADD UNIQUE KEY uq_ai_document_version_attachment (tenant_id, attachment_id);

UPDATE ai_document_version v
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY document_id, tenant_id ORDER BY create_time, id) AS next_version_no
    FROM ai_document_version
) numbered ON numbered.id=v.id
SET v.version_no=numbered.next_version_no;

ALTER TABLE ai_document_version
    ADD UNIQUE KEY uq_ai_document_version_number (document_id, tenant_id, version_no);

UPDATE ai_knowledge_space SET status='PUBLISHED' WHERE LOWER(status)='active';
UPDATE ai_knowledge_space SET status=UPPER(status);
UPDATE ai_document SET status=UPPER(status);
UPDATE ai_document_version SET status=UPPER(status);
