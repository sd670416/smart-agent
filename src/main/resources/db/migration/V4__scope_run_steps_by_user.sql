ALTER TABLE ai_run_step
    ADD COLUMN user_id varchar(36) NULL AFTER tenant_id;

UPDATE ai_run_step step_record
JOIN ai_run run_record
    ON run_record.id = step_record.run_id AND run_record.tenant_id = step_record.tenant_id
SET step_record.user_id = run_record.user_id;

ALTER TABLE ai_run_step
    MODIFY COLUMN user_id varchar(36) NOT NULL;

CREATE INDEX idx_ai_run_step_scope ON ai_run_step (tenant_id, user_id, run_id, step_no);
