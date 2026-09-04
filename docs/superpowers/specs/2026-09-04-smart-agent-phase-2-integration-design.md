# Smart Agent Phase 2 集成设计说明书

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 系统名称 | 工程管理智能体 |
| 文档类型 | Phase 2 集成设计说明书 |
| 日期 | 2026-09-04 |
| 涉及项目 | `smart-web`、`smart-boot`、`smart-agent` |
| 当前状态 | 设计已确认，待编写实施计划 |

## 2. 建设目标

Phase 2 建设可直接使用的智能对话与知识库垂直链路：

1. 在 `smart-web` 中提供独立的“智能助手”页面。
2. 支持按钮选择、拖拽和剪贴板粘贴上传文件，图片可在对话中预览。
3. 浏览器直接上传文件到阿里云 OSS，文件字节不经过应用服务器。
4. 提供独立的知识库管理页面和文档解析、发布流程。
5. 通过角色为用户分配知识库使用权限，并叠加项目数据权限。
6. 由 `smart-boot` 作为 AI BFF、安全边界和业务权限最终裁决者。
7. 由 `smart-agent` 管理会话、知识、检索、模型编排和审计数据。

本阶段不建设业务页面侧边助手，不允许在对话页面维护知识库，不修改原系统已有业务表结构。

## 3. 设计原则

- **低侵入**：不修改 `sys_role`、`sys_user` 等原系统业务表，不复制原系统的数据范围规则。
- **可信身份**：浏览器不能指定租户、用户、角色、项目或知识库权限范围。
- **职责隔离**：`smart-boot` 负责身份和业务权限，`smart-agent` 负责 AI 领域数据和能力。
- **直接上传**：文件字节从浏览器直达 OSS，应用服务器只签发策略并验证上传结果。
- **知识独立维护**：聊天附件与长期知识库采用不同的数据、索引和生命周期。
- **索引可重建**：MySQL 是知识元数据权威源，Qdrant 只保存可重建向量索引。
- **显式发布**：知识文档解析成功后先进入草稿，发布后才参与检索。
- **纵深校验**：角色授权和项目权限都必须满足，`smart-boot` 在业务工具执行时再次校验。

## 4. 总体架构

```text
smart-web
  |-- 智能助手、知识库管理、角色知识库授权
  |-- fetch + ReadableStream 消费 POST SSE
  |-- 浏览器直接上传文件到阿里云 OSS
  v
smart-boot / smart-ai
  |-- 复用当前登录态和原系统权限
  |-- 签发 OSS 上传策略并验证 OSS 对象
  |-- 代理普通 HTTP 与 SSE 请求
  |-- 签名 X-Agent-Context
  |-- 提供受控的项目权限和业务工具接口
  v
smart-agent
  |-- 会话、消息、运行和审计
  |-- 附件、知识库、文档版本和角色授权
  |-- 文件解析、切片、Embedding、检索和引用
  |-- 模型网关与对话编排
  |-- MySQL / Redis / Qdrant
  v
Alibaba Cloud OSS
```

浏览器只能访问 `smart-boot` 的公开 AI 接口和 OSS 上传地址，不能直接调用 `smart-agent` 内部接口。

## 5. 项目结构

### 5.1 smart-boot

新增独立 Maven 模块：

```text
F:\project\gongcheng\smart-boot\smart-ai
```

建议结构：

```text
smart-ai/src/main/java/com/smart/ai/
|-- controller/       浏览器 AI 接口
|-- internal/         smart-agent 内部接口
|-- security/         可信上下文生成与签名
|-- client/           smart-agent HTTP/SSE 客户端
|-- oss/              OSS Policy、对象 Key 和上传验证
|-- permission/       角色及项目权限适配
|-- tool/             受控业务工具适配
|-- model/            请求、响应和事件契约
`-- config/           模块配置
```

根 `pom.xml` 注册 `smart-ai`，`smart-app` 引入该模块。AI 代码不分散到原有业务模块，不修改现有实体和业务表。

### 5.2 smart-web

```text
src/views/ai/
|-- assistant/
|   |-- index.vue
|   |-- components/
|   `-- composables/
`-- knowledge/
    |-- index.vue
    |-- components/
    `-- composables/

src/components/ai/
|-- attachment-upload/
|-- image-preview/
`-- citation-viewer/

src/api/ai/
|-- conversation-api.js
|-- chat-api.js
|-- attachment-api.js
|-- knowledge-api.js
`-- role-grant-api.js
```

首个动态菜单组件为 `ai/assistant/index`，路由为 `/ai/assistant`。知识库管理使用独立页面。角色列表只增加“知识库权限”操作和独立弹窗，不改变原角色编辑表单的提交结构。

### 5.3 smart-agent

保持 Maven 单工程，按领域扩展：

```text
com.smart.agent
|-- conversation/
|-- attachment/
|-- knowledge/
|-- authorization/
|-- ingestion/
|-- retrieval/
|-- orchestration/
|-- model/
|-- tool/
`-- audit/
```

## 6. 知识库权限设计

### 6.1 权限规则

用户可以检索知识库必须同时满足：

```text
租户一致
AND 知识库已发布
AND 用户任一有效角色已绑定该知识库
AND（知识库为租户级 OR 用户拥有对应项目的数据权限）
```

多个角色的知识库权限取并集。角色授权不能扩大用户已有的项目数据范围。

一期知识库范围只支持：

- `TENANT`：租户公共知识库。
- `PROJECT`：项目专属知识库，必须绑定一个项目。

### 6.2 低侵入存储

角色与知识库的关系保存在 `smart-agent` 数据库：

```text
agent_role_knowledge_grant
- id
- tenant_id
- role_id
- knowledge_space_id
- status
- created_by
- created_at
```

`role_id` 只引用原系统角色编号，不建立跨数据库外键。删除角色后，可信上下文不再包含该角色，因此残留授权不会产生访问权限；后台任务可以清理无效授权。

### 6.3 运行时校验

1. `smart-boot` 从当前登录态取得可信租户、用户和有效角色。
2. `smart-boot` 签名并传递 `X-Agent-Context`，浏览器无法获得签名密钥。
3. `smart-agent` 验证签名、签发时间、过期时间和调用方。
4. `smart-agent` 根据可信角色查询知识库授权。
5. 对项目知识库，`smart-agent` 调用 `smart-boot` 内部接口复核项目权限。
6. 检索结果返回模型前，再从 MySQL 复核知识库、文档版本和发布状态。

新请求立即使用最新角色权限。单次运行使用请求开始时的权限快照以保持一致。历史回答保留审计引用，但用户再次查看引用原文时重新校验当前权限。

## 7. 知识库与聊天附件边界

- 对话页面只处理聊天和临时附件，不提供知识库新增、修改或“加入知识库”操作。
- 知识库由独立管理页面维护。
- 普通用户对话时不选择知识库，系统自动检索其角色有权使用且满足项目权限的全部已发布知识库。
- 聊天附件只用于当前会话，使用独立的临时向量索引并按保留期清理。
- 知识库文件长期保存，只有显式发布的文档版本参与检索。

## 8. OSS 直接上传

### 8.1 上传流程

1. 前端向 `smart-boot` 申请上传策略。
2. `smart-boot` 校验登录状态、上传用途和管理权限。
3. `smart-agent` 创建待上传记录并生成不可预测的对象 Key。
4. `smart-boot` 返回有效期建议为 5 分钟、仅允许目标 Key 的 OSS Policy。
5. 浏览器通过 `XMLHttpRequest` 或等价能力直接上传到 OSS并报告进度。
6. 前端调用上传完成接口。
7. 服务端读取 OSS 对象元数据，验证对象 Key、存在性、大小、ETag、租户、用户和用途。
8. 验证通过后将记录置为 `UPLOADED`，随后异步解析。

对象 Key：

```text
ai/{tenantId}/{purpose}/{yyyy/MM/dd}/{userId}/{uuid}.{ext}
```

`purpose` 取 `chat-attachment` 或 `knowledge-document`。MySQL 保存对象 Key，不把公开 URL 作为权威标识。

### 8.2 文件类型策略

上传阶段不限制扩展名和 `Content-Type`，但上传成功不代表 AI 可以解析：

- 服务端根据文件内容识别真实类型，不信任扩展名或浏览器 MIME。
- 已支持的类型进入解析和索引流程。
- 不支持的类型允许保存，状态为 `UNSUPPORTED`，不能参与问答或发布。
- 内容与扩展名明显冲突时记录风险，必要时置为 `QUARANTINED`。
- 压缩包、可执行文件和脚本不解压、不执行、不加载。
- 图片可识别时直接预览，其他文件显示通用图标并按权限提供下载。

一期保证解析 PDF、DOCX、XLSX、Markdown、TXT 和常见图片；其他类型可后续通过新增解析器支持并重新解析历史文件。

### 8.3 资源限制

文件类型不限，但资源使用必须限制并配置化：

| 限制项 | 默认值 |
|---|---:|
| 单个聊天附件 | 50 MB |
| 单个知识库文件 | 200 MB |
| 每条消息附件数 | 10 个 |
| 每条消息附件总量 | 100 MB |

公共读 URL 只用于预览和模型可访问的图片输入。系统应提示管理员公共读存储不适合高敏感材料，并保持未来切换私有读时接口契约不变。

## 9. 生命周期

聊天附件状态：

```text
PENDING_UPLOAD -> UPLOADED -> PROCESSING -> READY
                                 |            |
                                 v            v
                              FAILED       EXPIRED

任意解析阶段还可能进入 UNSUPPORTED 或 QUARANTINED
```

知识库文档状态：

```text
PENDING_UPLOAD -> UPLOADED -> PARSING -> INDEXING -> DRAFT -> PUBLISHED
                                      |              |          |
                                      v              v          v
                         FAILED / UNSUPPORTED     DISABLED    DISABLED

风险文件进入 QUARANTINED；删除采用先停用检索、后异步清理的 DELETED 终态。
```

重新上传知识库文档产生新版本，不直接覆盖已发布版本。解析成功只进入 `DRAFT`，必须由管理员发布。

## 10. 公开接口

浏览器统一调用 `smart-boot/smart-ai`：

```text
# 对话
POST   /ai/conversations
GET    /ai/conversations
GET    /ai/conversations/{id}/messages
DELETE /ai/conversations/{id}
POST   /ai/chat/stream
GET    /ai/runs/{id}
POST   /ai/runs/{id}/regenerate

# 临时附件
POST   /ai/attachments/upload-policy
POST   /ai/attachments/{id}/complete
GET    /ai/attachments/{id}
DELETE /ai/attachments/{id}

# 知识库
POST   /ai/knowledge-spaces
GET    /ai/knowledge-spaces
GET    /ai/knowledge-spaces/{id}
PUT    /ai/knowledge-spaces/{id}
POST   /ai/knowledge-spaces/{id}/publish
POST   /ai/knowledge-spaces/{id}/disable

# 知识库文档
POST   /ai/knowledge-spaces/{id}/documents/upload-policy
POST   /ai/knowledge-documents/{id}/complete
GET    /ai/knowledge-spaces/{id}/documents
GET    /ai/knowledge-documents/{id}
POST   /ai/knowledge-documents/{id}/publish
POST   /ai/knowledge-documents/{id}/disable
POST   /ai/knowledge-documents/{id}/retry
DELETE /ai/knowledge-documents/{id}

# 角色授权
GET    /ai/roles/{roleId}/knowledge-grants
PUT    /ai/roles/{roleId}/knowledge-grants
```

上传完成接口必须幂等：相同附件编号和 ETag 重复提交返回相同结果，ETag 不一致则拒绝。

聊天请求只接受附件编号：

```json
{
  "conversationId": "conversation-1",
  "content": "请分析这些资料",
  "attachmentIds": ["attachment-1"]
}
```

服务端验证附件属于当前租户、用户和会话且状态为 `READY`。请求不接受 OSS URL、知识库编号或用于覆盖可信权限的字段。

## 11. SSE 对话协议

前端使用 `fetch + ReadableStream` 发起 POST SSE，`smart-boot` 透传取消信号和经过过滤的 SSE 事件。

事件类型：

```text
run.started
message.accepted
attachment.processing
retrieval.started
citation
tool.started
tool.completed
answer.delta
answer.completed
run.failed
```

每个事件包含 `runId` 和递增的 `sequence`。前端按序号去重和排序。

用户停止时，前端中止请求，`smart-boot` 将取消传递给 `smart-agent`，后者停止后续模型和工具调用并记录 `CANCELLED`。已生成文本可以作为未完成消息保存。网络中断不自动重新执行，用户明确点击“重新生成”时创建新的 `runId`。

一期不实现 SSE 断点续传。前端可通过运行详情接口查询已持久化终态。

运行默认限制：

| 限制项 | 默认值 |
|---|---:|
| 总时长 | 90 秒 |
| 工具调用 | 5 次 |
| 模型调用 | 6 轮 |
| 引用数量 | 20 条 |
| 单个工具结果 | 64 KB |

## 12. 数据边界

AI 数据全部保存在 `smart-agent` 数据库，建议包含：

```text
agent_conversation
agent_message
agent_run
agent_run_step
agent_attachment
agent_knowledge_space
agent_knowledge_document
agent_knowledge_document_version
agent_knowledge_chunk
agent_role_knowledge_grant
agent_citation
agent_audit_log
```

所有业务查询包含 `tenant_id`，用户会话还必须按用户所有权隔离。

Qdrant Collection：

```text
agent_knowledge
agent_conversation_attachment
```

知识库索引长期保留；聊天附件索引带过期时间。MySQL 状态变化先阻止检索，再异步清理 Qdrant 和 OSS，避免删除过程中的数据泄露窗口。

## 13. 内部权限和工具接口

`smart-agent` 不读取原系统业务数据库，只调用 `smart-boot/smart-ai` 的受控内部接口，例如：

```text
POST /internal/ai/permissions/projects/check
POST /internal/ai/tools/project-overview
```

内部请求携带可信身份上下文、Trace ID、工具名、时间戳和防重放信息。`smart-boot` 每次执行工具时重新应用现有项目和数据范围规则，不能只依赖对话开始时的权限快照。

## 14. 错误处理与清理

稳定错误码包括：

```text
AI_UNAUTHORIZED
AI_FORBIDDEN
CONVERSATION_NOT_FOUND
ATTACHMENT_NOT_READY
ATTACHMENT_UNSUPPORTED
UPLOAD_POLICY_EXPIRED
KNOWLEDGE_FORBIDDEN
KNOWLEDGE_DOCUMENT_NOT_READY
MODEL_TIMEOUT
TOOL_TIMEOUT
RATE_LIMITED
STREAM_INTERRUPTED
INTERNAL_ERROR
```

SSE 建立前返回普通 HTTP JSON 错误；建立后使用 `run.failed` 事件。响应不得泄露堆栈、密钥、OSS Policy、内部地址或未经处理的供应商响应。

清理规则：

- 过期的待上传记录自动失效。
- 上传验证失败时标记失败并安排删除对象。
- 聊天附件最后使用后默认保留 7 天，再清理对象、解析文本和临时向量。
- 知识文档删除时先退出检索，再异步清理索引和 OSS 对象。
- 解析失败保留稳定错误码并允许有权限的管理员重试。

## 15. 审计

审计以下行为：

- 知识库创建、修改、发布、停用和删除；
- 文档上传、发布、撤回、删除和重新解析；
- 角色知识库授权变更；
- 回答发起、停止和重新生成；
- 业务工具调用；
- 权限拒绝和运行异常。

审计只保存必要摘要和对象编号，不保存密钥、上传策略或模型内部思维链。

## 16. 实施阶段

1. **契约与安全底座**：建立 `smart-ai` 模块、可信上下文、Agent 客户端、错误契约和 OSS 基础能力。
2. **会话垂直链路**：完成会话、历史消息、POST SSE、停止和重新生成，先使用确定性模拟模型。
3. **OSS 直传和聊天附件**：完成选择、拖拽、粘贴、进度、取消、预览、上传验证和临时清理。
4. **知识库独立维护**：完成知识空间、文档版本、异步解析、状态、发布、撤回、删除和重试。
5. **角色知识库授权**：完成授权管理、可信角色解析和项目知识库双重权限校验。
6. **RAG 与引用**：完成切片、Embedding、Qdrant 过滤、MySQL 状态复核和引用展示。
7. **真实业务工具**：接入项目概况等只读工具，由 `smart-boot` 做最终业务权限校验。
8. **联调与验收**：完成跨项目测试、配置说明、清理任务、审计和异常场景验证。

每个阶段独立测试、提交和审查，避免三个仓库同时形成大范围不可验证改动。

## 17. 测试设计

### 17.1 smart-agent

- 租户、用户、角色、知识库和项目隔离测试。
- 知识文档版本、解析、发布、撤回和删除测试。
- Qdrant 权限过滤、MySQL 状态复核和引用准确性测试。
- SSE 正常、取消、超时和异常测试。
- 临时附件过期清理和幂等完成测试。

### 17.2 smart-boot

- 登录用户无法伪造身份或权限字段。
- OSS Policy 的对象 Key、用途和过期限制。
- OSS 对象存在性、大小和 ETag 验证。
- 角色管理权限和项目权限复核。
- SSE 代理取消、背压和错误映射。

### 17.3 smart-web

- 文件选择、拖拽和粘贴上传。
- 上传进度、取消、失败重试和重复文件处理。
- 图片预览及不支持解析提示。
- SSE 增量渲染、停止和重新生成。
- 无权限用户看不到知识库管理和角色授权入口。

### 17.4 联调验收

- 文件内容不经过 `smart-boot` 或 `smart-agent`。
- 普通用户不能维护或授权知识库。
- 角色变化立即影响新的对话请求。
- 用户不能通过项目知识库读取无权项目内容。
- 草稿、停用和删除文档不参与新回答。
- 对话回答展示可定位的文件、版本、页码、章节和原文引用。
- 原系统已有业务表结构不发生变化。
- 所有关键操作均可通过 Trace ID 和审计记录追踪。

## 18. 完成标准

Phase 2 在以下条件全部满足后完成：

1. `smart-web -> smart-boot -> smart-agent` 的对话和 SSE 链路可用。
2. OSS 直传支持选择、拖拽、粘贴、进度和图片预览。
3. 上传完成经过服务端对象验证，聊天只提交附件编号。
4. 知识库在独立页面维护，文档显式发布后才参与检索。
5. 知识库权限由可信角色计算，项目知识同时校验项目数据权限。
6. AI 数据完全存放在 `smart-agent`，原业务表结构保持不变。
7. 回答可以返回权限过滤后的知识引用和受控业务工具结果。
8. 取消、失败、超时、清理和审计行为均有自动化测试覆盖。

