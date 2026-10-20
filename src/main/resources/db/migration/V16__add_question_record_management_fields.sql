ALTER TABLE ai_question_record
  ADD COLUMN description VARCHAR(2000) NULL,
  ADD COLUMN handling_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_question_tenant_status
  ON ai_question_record (tenant_id, handling_status, created_at, id);
