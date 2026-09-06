# Task 6 执行报告

## 结果

已完成解析器路由、异步摄取、知识与聊天附件向量集合隔离，以及过期附件清理。未开始 Task 7。

## RED 证据

- 命令：`mvn -Dtest=DocumentParserRegistryTest,DocumentIngestionJobTest test`
- 初始结果：因 `ContentTypeDetector`、`DocumentParserRegistry`、`DocumentIngestionJob` 等摄取类型尚不存在而编译失败。
- 清理测试在实现前同样因 `AttachmentCleanupJob` 和对象存储端口缺失而失败。

## GREEN 证据

- `mvn -Dtest=DocumentParserRegistryTest,DocumentIngestionJobTest,AttachmentCleanupJobTest test`
  - 12 项通过，0 失败，0 跳过。
- `mvn test`
  - 125 项通过，0 失败，0 跳过。
- `mvn -Dagent.it.mysql=true -Dtest=AttachmentPersistenceMySqlIT,KnowledgeManagementMySqlIT test`
  - MySQL 8.4.6 下 4 项通过；Flyway 成功执行 V1-V7；V7 字段和索引断言通过。
- `mvn -Dagent.it.qdrant.external=true -Dtest=ExternalQdrantVectorIndexIT test`
  - 远程 Qdrant 1.18.2 下 2 项通过；验证权限范围、租户隔离、幂等删除/重建和双集合隔离。

## 安全与数据边界

- 测试通过进程环境读取本地配置，未在源码、报告或提交中写入密钥。
- 外部 Qdrant 使用随机测试集合并在测试结束后删除。
- `.env`、`target/` 和 Task 7 内容不进入提交。

## 复审修复证据

- 修复附件清理候选查询：优先依据 `last_used_at` 判断保留期；该字段为空时才使用更新时间兜底，避免近期使用但更新时间较旧的附件被误删。
- 图片解析改为 ImageReader 读取头部宽高，新增最大宽度、最大高度和最大像素数限制，超限返回 `IMAGE_PIXEL_LIMIT`，不解码整个位图。
- RED：新增图片超限测试在实现前因 `ParseLimits` 缺少尺寸参数而编译失败。
- GREEN：`mvn -Dtest=DocumentParserRegistryTest,AttachmentCleanupJobTest test` 为 9/9；`mvn -Dagent.it.mysql=true -Dtest=AttachmentPersistenceMySqlIT test` 为 3/3；最终 `mvn test` 为 126/126。
