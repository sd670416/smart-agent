SET NAMES utf8mb4;

ALTER TABLE ai_conversation COMMENT = 'AI会话表';
ALTER TABLE ai_message COMMENT = 'AI会话消息表';
ALTER TABLE ai_run COMMENT = 'AI智能体运行记录表';
ALTER TABLE ai_run_step COMMENT = 'AI智能体运行步骤表';
ALTER TABLE ai_knowledge_space COMMENT = 'AI知识空间表';
ALTER TABLE ai_document COMMENT = 'AI知识文档表';
ALTER TABLE ai_document_version COMMENT = 'AI知识文档版本表';
ALTER TABLE ai_document_acl COMMENT = 'AI知识文档权限表';
ALTER TABLE ai_document_chunk COMMENT = 'AI知识文档分块表';
ALTER TABLE ai_ingestion_job COMMENT = 'AI文档解析任务表';
ALTER TABLE ai_attachment COMMENT = 'AI会话附件表';
ALTER TABLE ai_role_knowledge_grant COMMENT = 'AI角色知识空间授权表';
ALTER TABLE ai_citation COMMENT = 'AI回答引用记录表';
ALTER TABLE ai_audit_log COMMENT = 'AI业务操作审计日志表';
ALTER TABLE ai_model_call_log COMMENT = 'AI模型调用审计日志表';

DROP PROCEDURE IF EXISTS add_ai_column_comments;

DELIMITER $$
CREATE PROCEDURE add_ai_column_comments()
BEGIN
    DECLARE finished INTEGER DEFAULT 0;
    DECLARE current_table VARCHAR(64);
    DECLARE current_column VARCHAR(64);
    DECLARE current_type VARCHAR(255);
    DECLARE current_data_type VARCHAR(64);
    DECLARE current_nullable VARCHAR(3);
    DECLARE current_default TEXT;
    DECLARE current_extra VARCHAR(255);
    DECLARE current_charset VARCHAR(64);
    DECLARE current_collation VARCHAR(64);
    DECLARE column_description VARCHAR(255);
    DECLARE columns_cursor CURSOR FOR
        SELECT table_name, column_name, column_type, data_type, is_nullable,
               column_default, extra, character_set_name, collation_name
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name LIKE 'ai\_%'
         ORDER BY table_name, ordinal_position;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN columns_cursor;
    comment_loop: LOOP
        FETCH columns_cursor INTO current_table, current_column, current_type, current_data_type,
            current_nullable, current_default, current_extra, current_charset, current_collation;
        IF finished = 1 THEN LEAVE comment_loop; END IF;

        SET column_description = CASE
            WHEN current_column = 'id' THEN '主键ID'
            WHEN current_column = 'tenant_id' THEN '租户ID'
            WHEN current_column = 'user_id' THEN '用户ID'
            WHEN current_column = 'actor_user_id' THEN '操作用户ID'
            WHEN current_column = 'conversation_id' THEN '会话ID'
            WHEN current_column = 'run_id' THEN '运行记录ID'
            WHEN current_column = 'message_id' THEN '消息ID'
            WHEN current_column = 'space_id' THEN '知识空间ID'
            WHEN current_column = 'knowledge_space_id' THEN '知识空间ID'
            WHEN current_column = 'document_id' THEN '文档ID'
            WHEN current_column = 'document_version_id' THEN '文档版本ID'
            WHEN current_column = 'active_version_id' THEN '当前生效版本ID'
            WHEN current_column = 'attachment_id' THEN '附件ID'
            WHEN current_column = 'chunk_id' THEN '文档分块ID'
            WHEN current_column = 'organization_id' THEN '组织ID'
            WHEN current_column = 'project_id' THEN '项目ID'
            WHEN current_column = 'role_id' THEN '角色ID'
            WHEN current_column = 'identity_id' THEN '身份ID'
            WHEN current_column = 'title' THEN '标题'
            WHEN current_column = 'name' THEN '名称'
            WHEN current_column = 'description' THEN '描述'
            WHEN current_column = 'role' THEN '消息角色'
            WHEN current_column = 'content' THEN '内容'
            WHEN current_column = 'attachments_json' THEN '消息附件JSON'
            WHEN current_column = 'sequence_no' THEN '消息序号'
            WHEN current_column = 'step_no' THEN '步骤序号'
            WHEN current_column = 'step_type' THEN '步骤类型'
            WHEN current_column = 'status' THEN '状态'
            WHEN current_column = 'scope' THEN '知识空间范围'
            WHEN current_column = 'source_type' THEN '来源类型'
            WHEN current_column = 'source_text' THEN '解析后的源文本'
            WHEN current_column = 'source_checksum' THEN '源文件校验值'
            WHEN current_column = 'content_checksum' THEN '内容校验值'
            WHEN current_column = 'parser_version' THEN '解析器版本'
            WHEN current_column = 'embedding_model_key' THEN '向量模型标识'
            WHEN current_column = 'vector_point_id' THEN '向量数据库点位ID'
            WHEN current_column = 'ordinal_no' THEN '顺序号'
            WHEN current_column = 'page_number' THEN '原文页码'
            WHEN current_column = 'section_title' THEN '章节标题'
            WHEN current_column = 'quoted_text' THEN '引用文本'
            WHEN current_column = 'subject_type' THEN '授权主体类型'
            WHEN current_column = 'subject_id' THEN '授权主体ID'
            WHEN current_column = 'permission' THEN '权限类型'
            WHEN current_column = 'purpose' THEN '附件用途'
            WHEN current_column = 'original_filename' THEN '原始文件名'
            WHEN current_column = 'object_key' THEN '对象存储键'
            WHEN current_column = 'size_bytes' THEN '文件大小（字节）'
            WHEN current_column = 'etag' THEN '对象存储ETag'
            WHEN current_column = 'detected_media_type' THEN '识别出的媒体类型'
            WHEN current_column = 'failure_code' THEN '失败错误码'
            WHEN current_column = 'safe_error_code' THEN '脱敏错误码'
            WHEN current_column = 'trace_id' THEN '链路追踪ID'
            WHEN current_column = 'safe_summary' THEN '脱敏摘要'
            WHEN current_column = 'safe_input_summary' THEN '脱敏输入摘要'
            WHEN current_column = 'safe_output_summary' THEN '脱敏输出摘要'
            WHEN current_column = 'tool_execution_summaries' THEN '工具执行摘要'
            WHEN current_column = 'citation_summaries' THEN '引用摘要'
            WHEN current_column = 'action' THEN '操作动作'
            WHEN current_column = 'object_type' THEN '操作对象类型'
            WHEN current_column = 'object_id' THEN '操作对象ID'
            WHEN current_column = 'outcome' THEN '执行结果'
            WHEN current_column = 'model_name' THEN '模型名称'
            WHEN current_column = 'provider_url' THEN '模型服务地址'
            WHEN current_column = 'http_status' THEN 'HTTP状态码'
            WHEN current_column = 'duration_millis' THEN '耗时（毫秒）'
            WHEN current_column = 'input_tokens' THEN '输入Token数'
            WHEN current_column = 'output_tokens' THEN '输出Token数'
            WHEN current_column = 'safe_request_summary' THEN '脱敏请求摘要'
            WHEN current_column = 'safe_response_summary' THEN '脱敏响应摘要'
            WHEN current_column = 'error_type' THEN '错误类型'
            WHEN current_column = 'safe_error_detail' THEN '脱敏错误详情'
            WHEN current_column = 'attempt_count' THEN '尝试次数'
            WHEN current_column = 'upload_expires_at' THEN '上传凭证过期时间'
            WHEN current_column = 'last_used_at' THEN '最后使用时间'
            WHEN current_column = 'cleanup_completed_at' THEN '清理完成时间'
            WHEN current_column = 'started_time' THEN '开始时间'
            WHEN current_column = 'finished_time' THEN '结束时间'
            WHEN current_column = 'created_by' THEN '创建人ID'
            WHEN current_column = 'updated_by' THEN '更新人ID'
            WHEN current_column = 'create_time' THEN '创建时间'
            WHEN current_column = 'update_time' THEN '更新时间'
            WHEN current_column = 'deleted' THEN '逻辑删除标识'
            WHEN current_column = 'version' THEN '乐观锁版本号'
            WHEN current_column = 'version_no' THEN '文档版本号'
            ELSE CONCAT('字段：', current_column)
        END;

        SET @column_sql = CONCAT(
            'ALTER TABLE `', current_table, '` MODIFY COLUMN `', current_column, '` ', current_type,
            IF(current_charset IS NULL, '', CONCAT(' CHARACTER SET ', current_charset, ' COLLATE ', current_collation)),
            IF(current_nullable = 'NO', ' NOT NULL', ' NULL'),
            CASE
                WHEN current_default IS NULL THEN ''
                WHEN current_data_type IN ('tinyint','smallint','mediumint','int','integer','bigint','decimal','numeric','float','double','real','bit')
                    THEN CONCAT(' DEFAULT ', current_default)
                WHEN UPPER(current_default) LIKE 'CURRENT_TIMESTAMP%' THEN CONCAT(' DEFAULT ', current_default)
                ELSE CONCAT(' DEFAULT ', QUOTE(current_default))
            END,
            IF(current_extra IS NULL OR current_extra = '', '', CONCAT(' ', current_extra)),
            ' COMMENT ', QUOTE(column_description)
        );
        PREPARE column_statement FROM @column_sql;
        EXECUTE column_statement;
        DEALLOCATE PREPARE column_statement;
    END LOOP;
    CLOSE columns_cursor;
END$$
DELIMITER ;

CALL add_ai_column_comments();
DROP PROCEDURE add_ai_column_comments;
