-- 会话与运行记录绑定模型
-- 1. ai_conversation 保存会话当前选择的模型，新建会话继承系统默认模型
-- 2. ai_run 保存本次运行的模型快照，保证历史可追溯"当时用的是哪个模型"
--    快照字段与 ai_model_config.config_version 对应，配置变更后历史运行仍展示原配置信息
--
-- 注意：ai_conversation.model_id 与 ai_run.model_id 必须同时存在，
--       JpaModelConfigRepository#modelBindingColumnsPresent 依赖这两列判断引用关系是否可用。

ALTER TABLE ai_conversation
    ADD COLUMN model_id varchar(36) NULL COMMENT '会话当前选择的模型配置ID' AFTER title,
    ADD KEY idx_ai_conversation_model (model_id);

ALTER TABLE ai_run
    ADD COLUMN model_id varchar(36) NULL COMMENT '本次运行使用的模型配置ID' AFTER conversation_id,
    ADD COLUMN model_display_name varchar(128) NULL COMMENT '本次运行时模型展示名称快照' AFTER model_id,
    ADD COLUMN model_name varchar(128) NULL COMMENT '本次运行时实际请求模型名称快照' AFTER model_display_name,
    ADD COLUMN model_config_version bigint NULL COMMENT '本次运行时模型配置版本快照' AFTER model_name,
    ADD KEY idx_ai_run_model (model_id);

-- 不建立指向 ai_model_config 的外键：
-- 模型配置允许被删除，历史会话与运行仍需保留模型ID文字信息用于追溯。
