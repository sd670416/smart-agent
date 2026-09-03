# Qdrant 索引重建运行手册

## 原则

MySQL 中的知识空间、文档、版本和已发布 chunk 元数据是权威数据源；Qdrant 只是可重建的向量索引。重建不得修改 MySQL 元数据，也不得把草稿、已删除或其他租户的数据写入目标集合。整个过程使用最小权限账号，并通过环境变量提供 API key。

## 重建流程

1. 记录当前集合名称、每个租户已发布 chunk 数量和当前应用版本，确认没有正在进行的发布任务。
2. 创建一个全新的、带唯一版本后缀的 Qdrant collection，向量维度和距离必须与 `AGENT_QDRANT_VECTOR_DIMENSION` 及应用配置一致。
3. 为 `tenant_id`、`space_id`、`project_id`、`status` 创建 Keyword payload index。
4. 按租户从 MySQL 流式读取已发布且未删除的 chunk，使用当前配置的 embedding gateway 生成向量，并批量写入新 collection。每个 payload 至少包含 `chunk_id`、`document_id`、`tenant_id`、`space_id`、`project_id`、`status` 和内容；不要把完整原文写入运行日志。
5. 对每个租户校验 MySQL 已发布 chunk 数与 Qdrant 新集合中的点数，抽样验证 tenant、knowledge space、project 和 `published` 过滤条件，并确认没有跨租户命中。
6. 在低流量窗口把应用配置的 collection alias 从旧集合切换到新集合，先保留旧集合用于回滚；不要通过直接修改正在使用的集合来完成切换。
7. 观察应用健康检查、检索错误率和引用结果。确认新集合稳定且回滚窗口结束后，才删除旧集合。

## 操作约束

使用 Qdrant 官方 API 或受控运维脚本执行 collection、alias 和点操作；命令中的地址、API key、数据库密码使用环境变量或密钥管理注入，不要把真实值粘贴到文档、Shell 历史或工单中。任何计数不一致、schema 不匹配或租户过滤异常都应停止切换并保留旧集合。

应用启动时会校验集合向量 schema，并确保必要的 payload index 存在。重建完成后重新执行应用健康检查和带租户/项目范围的检索验收；只有验证通过后才能清理旧 collection。
