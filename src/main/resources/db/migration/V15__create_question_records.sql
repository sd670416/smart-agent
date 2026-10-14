ALTER TABLE ai_run ADD COLUMN user_message_id VARCHAR(36) NULL, ADD COLUMN assistant_message_id VARCHAR(36) NULL;
CREATE TABLE ai_question_record (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(36) NOT NULL, user_id VARCHAR(36) NOT NULL,
 conversation_id VARCHAR(36) NOT NULL, run_id VARCHAR(36) NOT NULL, trace_id VARCHAR(128),
 question TEXT NOT NULL, answer TEXT NOT NULL, domain VARCHAR(64),
 source_system BOOLEAN NOT NULL DEFAULT FALSE, source_model BOOLEAN NOT NULL DEFAULT FALSE, source_user BOOLEAN NOT NULL DEFAULT FALSE,
 reason_code VARCHAR(128) NOT NULL, reason TEXT NOT NULL,
 context_json LONGTEXT NOT NULL, steps_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, event_cutoff TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_question_tenant_run UNIQUE (tenant_id, run_id),
 INDEX idx_question_tenant_time (tenant_id, created_at, id)
);
