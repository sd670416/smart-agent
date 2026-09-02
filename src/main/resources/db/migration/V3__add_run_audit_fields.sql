ALTER TABLE ai_run
    ADD COLUMN trace_id varchar(128),
    ADD COLUMN safe_error_code varchar(128),
    ADD COLUMN input_tokens int NOT NULL DEFAULT 0,
    ADD COLUMN output_tokens int NOT NULL DEFAULT 0,
    ADD COLUMN tool_execution_summaries LONGTEXT,
    ADD COLUMN citation_summaries LONGTEXT;
