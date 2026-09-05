# 二阶段本地集成运行手册

## 启动顺序

1. 启动 MySQL、Redis 和 Qdrant（Qdrant 默认 HTTP `192.168.18.179:6333`、gRPC `192.168.18.179:6334）。
2. 在 IDEA 中启动 `smart-agent`（Java 21）和 `smart-boot/smart-app`（Java 8）。
3. 使用 `smart-web` 的 `npm run localhost` 启动管理端。

## 健康检查

- Agent：`/actuator/health`
- Boot：`/actuator/health`
- Qdrant：`http://192.168.18.179:6333/collections`

## 冒烟验证

先上传附件并确认状态为 READY，再在智能助手发送消息；知识库需先创建空间、上传文档并发布，角色授权变更应在下一次请求立即生效。浏览器网络面板应显示文件字节直接发送到 OSS，而非经过 Boot。

## 常见问题

- `AI_ATTACHMENT_NOT_READY`：等待解析完成或重新上传。
- `AI_KNOWLEDGE_ACCESS_DENIED`：检查角色授权和项目数据范围交集。
- SSE 中断：查询运行状态后决定停止或重新生成，不自动重放提示词。

所有密钥只通过环境变量提供，禁止提交 `.env`。
