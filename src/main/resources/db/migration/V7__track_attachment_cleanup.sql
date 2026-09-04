ALTER TABLE ai_attachment
    ADD COLUMN cleanup_completed_at datetime(3) NULL AFTER last_used_at,
    ADD KEY idx_ai_attachment_cleanup (purpose, cleanup_completed_at, status, update_time, id);
