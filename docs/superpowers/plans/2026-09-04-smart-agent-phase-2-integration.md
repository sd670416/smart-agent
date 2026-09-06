# 智能 Agent 第 2 阶段集成实施计划

> **面向智能体执行者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，按任务逐项实施本计划。步骤使用 checkbox（`- [ ]`）语法进行跟踪。

**目标：** 在 `smart-web` 交付一个按角色授权的工程助手，通过新的 `smart-boot/smart-ai` 模块进行安全代理，提供 OSS 直传、独立管理的知识库、引用以及经审计的 SSE 对话。

**架构：** `smart-web` 仅调用 `smart-boot`，将文件字节直接上传至可公开读取的阿里云 OSS，并使用 `fetch` 消费 POST SSE。新的 Java 8 `smart-ai` BFF 从现有登录态派生可信身份、签署内部上下文、校验 OSS 对象并代理 `smart-agent`；Java 21 `smart-agent` 负责所有 AI 数据、角色到知识库授权、解析、检索、编排及审计记录。

**技术栈：** Vue 3.4、Ant Design Vue 4.2.1、Pinia、Vite 5、Java 8、Spring Boot 2.7.9、Maven 多模块、Java 21、Spring Boot 3.5.16、LangChain4j 1.19.0、MySQL、Redis、Qdrant 1.18.3 客户端、阿里云 OSS。

**规格说明：** `docs/superpowers/specs/2026-09-04-smart-agent-phase-2-integration-design.md`

## 全局约束

- 不得修改现有 `smart-boot` 业务表结构，也不得向现有用户和角色实体添加字段。
- 所有 AI 领域数据（包括角色到知识库授权）均存储在 `smart-agent` 数据库中。
- 浏览器绝不能获取 Agent 上下文签名密钥，也不能直接调用内部 `smart-agent` 端点。
- 文件字节必须从浏览器直接流向 OSS，不得经过 `smart-boot` 或 `smart-agent`。
- 上传接受所有文件扩展名和 MIME 类型；解析和发布仍受能力限制。
- 绝不执行或解包归档文件、脚本或可执行附件。
- 聊天附件和知识文档必须使用独立的记录、索引和保留策略。
- 聊天请求仅接受附件 ID，绝不接受客户端提供的 OSS URL 或知识空间 ID。
- 知识访问要求租户相同、知识空间已发布、具备角色授权，且项目范围空间还需具备项目权限。
- Qdrant 是可重建索引；MySQL 始终是文档和发布状态的权威来源。
- 保持 `smart-boot` 的 Java 8 兼容性及 `smart-agent` 的 Java 21 兼容性。
- 使用 TDD，先运行聚焦测试再运行完整测试，并在所属仓库中独立提交每项任务。
- 绝不提交 `.env`、OSS 密钥、模型密钥、密码或签名密钥。

---

## 文件映射

### smart-boot

- `pom.xml`：注册 `smart-ai` Maven 模块。
- `smart-app/pom.xml`：将 `smart-ai` 加载到运行中的应用。
- `smart-app/src/main/resources/application-*.yml`：绑定非敏感的 AI 端点和超时配置。
- `smart-ai/pom.xml`：模块依赖和测试支持。
- `smart-ai/src/main/java/com/smart/ai/config/AiProperties.java`：带类型的 Agent、OSS、超时和限额配置。
- `smart-ai/src/main/java/com/smart/ai/security/AgentContextFactory.java`：从 `AuthUtil` 和现有授权服务派生可信上下文。
- `smart-ai/src/main/java/com/smart/ai/security/AgentContextSigner.java`：创建短时效 HMAC 上下文令牌。
- `smart-ai/src/main/java/com/smart/ai/client/SmartAgentClient.java`：JSON 请求代理。
- `smart-ai/src/main/java/com/smart/ai/client/SmartAgentSseClient.java`：感知取消的 SSE 代理。
- `smart-ai/src/main/java/com/smart/ai/oss/AiOssPolicyService.java`：创建精确键、短时效的 POST 策略。
- `smart-ai/src/main/java/com/smart/ai/oss/AiOssObjectVerifier.java`：上传后校验对象元数据。
- `smart-ai/src/main/java/com/smart/ai/controller/*`：公共 BFF 端点。
- `smart-ai/src/main/java/com/smart/ai/internal/*`：项目权限和只读工具端点。

### smart-agent

- `src/main/resources/db/migration/V5__create_attachment_and_knowledge_management_tables.sql`：附件、知识空间、版本、授权、引用和审计架构。
- `src/main/java/com/smart/agent/attachment/*`：上传登记、状态机、校验和清理。
- `src/main/java/com/smart/agent/knowledge/manage/*`：知识空间和文档生命周期。
- `src/main/java/com/smart/agent/authorization/*`：角色授权解析和项目范围交集。
- `src/main/java/com/smart/agent/ingestion/*`：解析器路由和异步索引。
- `src/main/java/com/smart/agent/chat/ChatCommand.java`：仅接受附件 ID。
- `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`：附件上下文和经授权的知识检索。
- `src/main/java/com/smart/agent/chat/ChatController.java`：兼容管理端的 SSE 合约。
- `src/main/java/com/smart/agent/audit/*`：安全与管理审计事件。

### smart-web

- `src/api/ai/*`：BFF API 和 POST SSE 解析器。
- `src/components/ai/attachment-upload/*`：OSS 直传、校验、进度、取消、拖放和粘贴。
- `src/views/ai/assistant/*`：对话工作区和引用。
- `src/views/ai/knowledge/*`：独立的知识管理工作区。
- `src/views/system/role/components/role-list/index.vue`：仅添加知识库权限操作和弹窗触发器。

---

### 任务 1：冻结跨服务合约并引导初始化 `smart-ai`

**文件：**
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/pom.xml`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/config/AiProperties.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/model/AgentContextPayload.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/model/AiErrorResponse.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/config/AiModuleContextTest.java`
- 修改：`F:/project/gongcheng/smart-boot/pom.xml`
- 修改：`F:/project/gongcheng/smart-boot/smart-app/pom.xml`
- 修改：`F:/project/gongcheng/smart-boot/smart-app/src/main/resources/application.yml`

**接口：**
- 产出：供后续所有 BFF 任务使用的 `AiProperties`、`AgentContextPayload` 和稳定的 `AI_*` 错误信封。
- `AgentContextPayload` 字段：`tenantId`、`userId`、`identityId`、`roleIds`、`issuedAt`、`expiresAt`、`nonce`。

- [ ] **步骤 1：编写会失败的模块上下文测试**

```java
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AiProperties.class)
@TestPropertySource(properties = {
    "smart.ai.agent-base-url=http://127.0.0.1:8080",
    "smart.ai.context-ttl-seconds=300"
})
public class AiModuleContextTest {
    @Autowired private AiProperties properties;

    @Test
    public void bindsAgentConfiguration() {
        assertEquals("http://127.0.0.1:8080", properties.getAgentBaseUrl());
        assertEquals(300, properties.getContextTtlSeconds());
    }
}
```

- [ ] **步骤 2：运行聚焦测试，并确认其因缺少 `smart-ai` 而失败**

运行：`mvn -pl smart-ai -am -Dtest=AiModuleContextTest test`

预期：Maven 找不到模块或测试类。

- [ ] **步骤 3：添加模块、配置类和不可变 DTO**

使用 `@ConfigurationProperties(prefix = "smart.ai")`；上下文 TTL 默认 300 秒，聊天超时 90 秒，单个聊天文件 50 MB，单个知识文件 200 MB，附件数 10 个，消息总量 100 MB。在应用配置中通过环境变量替换密钥。

- [ ] **步骤 4：运行模块和应用打包测试**

运行：`mvn -pl smart-ai -am test`

运行：`mvn -pl smart-app -am -DskipTests package`

预期：两条命令均在 Java 8 下成功。

- [ ] **步骤 5：在 `smart-boot` 中提交**

```bash
git add pom.xml smart-app/pom.xml smart-app/src/main/resources/application.yml smart-ai
git commit -m "feat: bootstrap smart ai bff module"
```

### 任务 2：可信上下文签名及 Agent HTTP/SSE 客户端

**文件：**
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/security/AgentContextFactory.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/security/AgentContextSigner.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/client/SmartAgentClient.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/client/SmartAgentSseClient.java`
- 创建：`F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/client/SmartAgentExceptionMapper.java`
- 测试：`F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/security/AgentContextSignerTest.java`
- 测试：`F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/client/SmartAgentClientTest.java`

**接口：**
- 使用：现有的 `AuthUtil.getUserId()` 和 `AuthUtil.getIdentityId()`，以及在本任务中发现的现有租户和角色服务。
- 产出：`String AgentContextSigner.sign(AgentContextPayload payload)`，以及始终附带 `X-Agent-Context` 和 `X-Trace-Id` 的感知取消 Agent 客户端方法。

- [ ] **步骤 1：添加签名合约测试**

测试紧凑令牌具有三个以点分隔的段；角色 ID 变化时令牌变化；启动时拒绝空密钥；签名前排序角色 ID；且绝不序列化客户端提供的租户或用户值。

- [ ] **步骤 2：运行签名测试，并验证缺失实现会失败**

运行：`mvn -pl smart-ai -Dtest=AgentContextSignerTest test`

预期：因缺少签名器和工厂类而编译失败。

- [ ] **步骤 3：实现可信上下文创建和 HMAC-SHA256 签名**

```java
public interface AgentContextSigner {
    String sign(AgentContextPayload payload);
}

public interface AgentContextFactory {
    AgentContextPayload currentContext();
}
```

对于缺少租户、用户、身份、角色集合、密钥或 TTL 非正数的情况，使用稳定的内部异常拒绝。

- [ ] **步骤 4：添加 WireMock 客户端测试**

断言 JSON 代理、已签名请求头、Trace ID 透传、非 2xx 错误映射、SSE Content-Type 保留和下游取消。

- [ ] **步骤 5：使用 Spring `RestTemplate` 实现 JSON 客户端，使用 `WebClient` 实现流式客户端**

从 `AiProperties` 约束连接、响应和总超时。绝不记录授权请求头、已签名上下文、含密钥的响应体或 OSS 策略。

- [ ] **步骤 6：运行聚焦测试和模块测试**

运行：`mvn -pl smart-ai -Dtest=AgentContextSignerTest,SmartAgentClientTest test`

运行：`mvn -pl smart-ai test`

预期：所有测试通过。

- [ ] **步骤 7：在 `smart-boot` 中提交**

```bash
git add smart-ai
git commit -m "feat: sign and proxy trusted agent context"
```

### 任务 3：Agent 附件领域模型与持久化

**文件：**
- 创建：`F:/project/gongcheng/agent/src/main/resources/db/migration/V5__create_attachment_and_knowledge_management_tables.sql`
- 创建：`F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/Attachment.java`
- 创建：`F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentPurpose.java`
- 创建：`F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentStatus.java`
- 创建：`F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentRepository.java`
- 创建：`F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/JpaAttachmentRepository.java`
- 创建：`F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentService.java`
- 测试：`F:/project/gongcheng/agent/src/test/java/com/smart/agent/attachment/AttachmentServiceTest.java`
- 测试：`F:/project/gongcheng/agent/src/test/java/com/smart/agent/AttachmentPersistenceMySqlIT.java`

**接口：**
- 产出：`registerUpload`、`completeUpload`、`markProcessing`、`markReady`、`markUnsupported`、`quarantine`、`fail` 和 `expire` 状态转换。
- `AttachmentPurpose`：`CHAT_ATTACHMENT`、`KNOWLEDGE_DOCUMENT`。
- `AttachmentStatus`：`PENDING_UPLOAD`、`UPLOADED`、`PROCESSING`、`READY`、`FAILED`、`UNSUPPORTED`、`QUARANTINED`、`EXPIRED`。

- [ ] **步骤 1：编写状态机单元测试**

覆盖有效转换；拒绝 `PENDING_UPLOAD -> READY`；相同 ETag 的完成操作保持幂等；拒绝不同 ETag；强制租户/用户所有权；并拒绝已过期上传的完成操作。

- [ ] **步骤 2：运行单元测试，并确认缺少领域类型时失败**

运行：`mvn -Dtest=AttachmentServiceTest test`

预期：因缺少附件类而编译失败。

- [ ] **步骤 3：实现不依赖 OSS 的聚合和服务**

```java
public record CompleteUploadCommand(
    UUID attachmentId, String tenantId, String userId,
    String objectKey, long size, String etag, String detectedMediaType) {}
```

在服务端使用租户、用途、UTC 日期、用户、UUID 和清理后的扩展名生成对象键。完成时绝不接受任意对象键。

- [ ] **步骤 4：添加 V5 迁移和 MySQL 集成测试**

创建附件、知识空间、知识文档、文档版本、角色授权、引用和审计表，建立租户前导索引，并确保 `(tenant_id, role_id, knowledge_space_id)` 和 `(tenant_id, object_key)` 唯一。

- [ ] **步骤 5：运行附件测试和非 Docker 回归测试**

运行：`mvn -Dtest=AttachmentServiceTest test`

运行：`mvn test`

预期：所有非 Docker 测试通过。

- [ ] **步骤 6：Docker 可用时运行 MySQL 集成测试**

运行：`mvn -Dit.test=AttachmentPersistenceMySqlIT verify`

预期：迁移已应用且租户范围约束通过。若 Docker 不可用，记录这一项延后检查，但不得将其标记为通过。

- [ ] **步骤 7：在 `smart-agent` 中提交**

```bash
git add src/main/resources/db/migration/V5__create_attachment_and_knowledge_management_tables.sql src/main/java/com/smart/agent/attachment src/test/java/com/smart/agent/attachment src/test/java/com/smart/agent/AttachmentPersistenceMySqlIT.java
git commit -m "feat: persist managed agent attachments"
```

### 任务 4：OSS 策略及已验证的上传完成流程

**文件：**
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/oss/AiAttachmentObjectKeyFactory.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/oss/AiOssPolicyService.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/oss/AiOssObjectVerifier.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiAttachmentController.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentController.java`
- 测试： `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/oss/AiOssPolicyServiceTest.java`
- 测试： `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiAttachmentControllerTest.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/attachment/AttachmentControllerTest.java`

**接口：**
- 公共 BFF：`POST /ai/attachments/upload-policy`、`POST /ai/attachments/{id}/complete`、`GET /ai/attachments/{id}`、`DELETE /ai/attachments/{id}`。
- Agent 内部登记返回 `attachmentId`、精确的 `objectKey`、`purpose`、`expiresAt` 及配置的最大字节数。
- 完成请求仅包含 `etag`；BFF 从 OSS 元数据取得权威的大小、媒体类型和对象键。

- [ ] **步骤 1：编写策略和控制器安全测试**

断言五分钟过期、精确键条件、仅限大小、不设扩展名/MIME 允许列表、不可猜测的 UUID 键、知识上传所需权限，以及 OSS 元数据与已登记对象不同时被拒绝。

- [ ] **步骤 2：运行聚焦测试并验证其失败**

运行：`mvn -pl smart-ai -Dtest=AiOssPolicyServiceTest,AiAttachmentControllerTest test`

预期：因缺少 OSS 服务和控制器而编译失败。

- [ ] **步骤 3：实现登记、策略生成和元数据校验**

在兼容处使用现有 `smart-boot` OSS 配置。保持策略接口与供应商无关：

```java
public interface AiOssPolicyService {
    DirectUploadPolicy create(String objectKey, long maxBytes, Instant expiresAt);
}

public interface AiOssObjectVerifier {
    VerifiedObject verify(String objectKey, long maxBytes, String expectedEtag);
}
```

- [ ] **步骤 4：实现 Agent 附件端点和 BFF 代理**

通过严格的请求 DTO 反序列化，拒绝名为 `tenantId`、`userId`、`objectKey`、`knowledgeSpaceIds` 或 `ossUrl` 的客户端字段。

- [ ] **步骤 5：运行两个仓库的测试套件**

在 `smart-boot` 中运行：`mvn -pl smart-ai test`

在 `agent` 中运行：`mvn -Dtest=AttachmentControllerTest,AttachmentServiceTest test`

预期：所有测试通过。

- [ ] **步骤 6：分别提交**

在 `smart-agent` 中：

```bash
git add src/main/java/com/smart/agent/attachment src/test/java/com/smart/agent/attachment
git commit -m "feat: expose verified attachment lifecycle"
```

在 `smart-boot` 中：

```bash
git add smart-ai
git commit -m "feat: issue verified oss direct uploads"
```

### 任务 5：知识管理与文档版本控制

**文件：**
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeSpace.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeScope.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeStatus.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeDocumentVersion.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeManagementService.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeManagementController.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiKnowledgeController.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/knowledge/manage/KnowledgeManagementServiceTest.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/knowledge/manage/KnowledgeManagementControllerTest.java`

**接口：**
- 产出设计第 10 节规定的所有知识空间和知识文档端点。
- `KnowledgeScope`：`TENANT`、`PROJECT`；`PROJECT` 必须恰好要求一个项目 ID。
- 文档版本只能从 `DRAFT` 发布；不受支持或已隔离的内容不能发布。

- [x] **步骤 1：编写生命周期与授权测试**

覆盖租户/项目创建校验、上传关联、版本递增、仅草稿可发布、原已发布版本的原子替换、先禁用后删除行为、租户隔离以及禁止非管理员调用。

- [x] **步骤 2：运行测试并确认缺少管理类型时失败**

运行： `mvn -Dtest=KnowledgeManagementServiceTest,KnowledgeManagementControllerTest test`

预期：编译失败。

- [x] **步骤 3：实现仓储与生命周期服务**

```java
public interface KnowledgeManagementService {
    KnowledgeSpace createSpace(CreateKnowledgeSpaceCommand command, AgentUserContext actor);
    KnowledgeDocumentVersion attachUploadedDocument(UUID spaceId, UUID attachmentId, AgentUserContext actor);
    KnowledgeDocumentVersion publishVersion(UUID versionId, AgentUserContext actor);
    void disableVersion(UUID versionId, AgentUserContext actor);
    void deleteDocument(UUID documentId, AgentUserContext actor);
}
```

在 MySQL 中以事务方式变更状态。仅在权威状态不再允许检索后发出清理工作。

- [x] **步骤 4：实现 Agent 内部控制器和 BFF 控制器**

对空间和文档列表使用分页。返回稳定状态、解析器错误码、活动版本、创建者和时间戳；绝不返回 OSS 凭据。

- [x] **步骤 5：运行回归测试套件**

在 `agent` 中运行： `mvn test`

在 `smart-boot` 中运行： `mvn -pl smart-ai test`

预期：所有非 Docker 测试通过。

- [x] **步骤 6：在两个仓库中提交**

Agent 提交： `feat: manage versioned knowledge documents`

Boot 提交： `feat: proxy knowledge management APIs`

### 任务 6：解析器路由、异步摄取与清理

**文件：**
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/ContentTypeDetector.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/DocumentParser.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/DocumentParserRegistry.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/DocumentIngestionJob.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentCleanupJob.java`
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/KnowledgeIngestionService.java`
- 修改： `F:/project/gongcheng/agent/pom.xml`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/ingestion/DocumentParserRegistryTest.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/ingestion/DocumentIngestionJobTest.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/attachment/AttachmentCleanupJobTest.java`

**接口：**
- `DocumentParser.supports(DetectedContentType)` 根据检测到的字节选择解析器，绝不单独依据文件名。
- `DocumentParser.parse(InputStream, ParseLimits)` 返回带页/工作表/章节定位信息的文本块。
- 不支持的类型转换为 `UNSUPPORTED`；可疑不匹配转换为 `QUARANTINED`。

- [x] **步骤 1：添加会失败的解析器路由测试**

测试 PDF、DOCX、XLSX、Markdown、TXT、常见图像检测、未知二进制文件处理、可执行文件签名隔离、扩展名与内容不匹配、解压限制以及页数/字符数限制。

- [x] **步骤 2：运行聚焦测试并确认其失败**

运行： `mvn -Dtest=DocumentParserRegistryTest,DocumentIngestionJobTest test`

预期：因缺少摄取类而编译失败。

- [x] **步骤 3：添加 Apache Tika 检测和专用解析器**

使用流式读取和明确的最大提取字符数，将归档深度设为零，禁用嵌入资源提取和公式执行；在配置视觉模型可用前，图像仅处理元数据。

- [x] **步骤 4：将摄取流程接入现有分块、嵌入和 `VectorIndex` 合约**

为已发布知识和会过期的对话附件使用独立的 Qdrant 命名空间/集合。包含租户、空间、项目、文档、版本、发布状态、附件和过期载荷字段。

- [x] **步骤 5：实现保留期清理**

以有界分页选择过期聊天附件，将其标记为 `EXPIRED`，删除临时向量，再通过供应商端口删除 OSS 对象。重试必须幂等。

- [x] **步骤 6：运行单元测试和 Qdrant 测试**

运行： `mvn test`

当 Qdrant 可访问时运行：`mvn -Dit.test=ExternalQdrantVectorIndexIT verify`

预期：单元测试通过；外部集成测试证明租户和索引相互隔离。

- [x] **步骤 7：在 `smart-agent` 中提交**

```bash
git add pom.xml src/main/java/com/smart/agent/ingestion src/main/java/com/smart/agent/attachment src/main/java/com/smart/agent/knowledge src/test/java/com/smart/agent/ingestion src/test/java/com/smart/agent/attachment
git commit -m "feat: ingest and clean managed documents"
```

### 任务 7：角色授权与项目权限交集

**文件：**
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/RoleKnowledgeGrant.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/RoleKnowledgeGrantService.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/KnowledgeAccessResolver.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/ProjectPermissionClient.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/RoleKnowledgeGrantController.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiRoleKnowledgeController.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiProjectPermissionController.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/authorization/KnowledgeAccessResolverTest.java`
- 测试： `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/internal/AiProjectPermissionControllerTest.java`

**接口：**
- 公共 BFF：`GET` 和 `PUT /ai/roles/{roleId}/knowledge-grants`。
- 内部权限检查：`POST /internal/ai/permissions/projects/check` 仅返回当前对可信用户可见的请求项目 ID。
- `KnowledgeAccessResolver.resolve(AgentUserContext)` 返回已发布的租户空间，以及通过权限交集的项目空间。

- [x] **步骤 1：编写会失败的授权测试**

测试角色并集、无角色拒绝、租户隔离、排除未发布空间、项目权限交集、陈旧的已删除角色授权、未经授权的授权管理，以及对新请求的即时生效。

- [x] **步骤 2：运行聚焦测试并验证失败**

在 `agent` 中运行： `mvn -Dtest=KnowledgeAccessResolverTest test`

在 `smart-boot` 中运行： `mvn -pl smart-ai -Dtest=AiProjectPermissionControllerTest test`

预期：因缺少类型或端点而失败。

- [x] **步骤 3：实现授权替换和访问解析**

```java
public Set<UUID> replaceGrants(
    String tenantId, String roleId, Set<UUID> knowledgeSpaceIds, AgentUserContext actor);

public Set<AuthorizedKnowledgeSpace> resolve(AgentUserContext context);
```

验证每个目标空间均属于该租户。以事务方式替换授权，并审计替换前后集合。

- [x] **步骤 4：实现项目权限适配器，且不复制业务规则**

定位并调用 `smart-boot` 内现有的项目数据范围服务。适配器只能返回交集；不得实现第二套项目权限算法。

- [x] **步骤 5：运行模块和 Agent 回归测试**

运行： `mvn -pl smart-ai test`

运行： `mvn test`

预期：所有非 Docker 测试通过。

- [x] **步骤 6：独立提交**

Agent 提交： `feat: authorize knowledge by role and project`

Boot 提交： `feat: expose trusted project permission checks`

### 任务 8：扩展对话编排与 SSE

**文件：**
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatCommand.java`
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatEvent.java`
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatController.java`
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/KnowledgeSearchService.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ConversationQueryController.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiConversationController.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiChatController.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/chat/AuthorizedChatControllerIT.java`
- 测试： `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiChatControllerTest.java`

**接口：**
- 聊天接受 `conversationId`、`content` 和最多十个 `attachmentIds`；未知 JSON 属性校验失败。
- 发出 `run.started`、`message.accepted`、`attachment.processing`、`retrieval.started`、`citation`、`tool.started`、`tool.completed`、`answer.delta`、`answer.completed` 和 `run.failed`。
- 每个事件均包含 `runId` 和单调递增的 `sequence`。

- [x] **步骤 1：编写会失败的端到端对话合约测试**

覆盖已授权知识检索、项目访问拒绝、就绪附件上下文、附件所有权拒绝、不支持附件拒绝、引用先于完成事件、序列单调递增、取消后转为 `CANCELLED`、不自动重放，以及重新生成时创建新的运行。

- [x] **步骤 2：运行 Agent 对话测试并确认新用例失败**

运行： `mvn -Dtest=AuthorizedChatControllerIT,ChatControllerIT test`

预期：因尚未集成附件和已授权空间而测试失败。

- [x] **步骤 3：使用明确限额和权限检查扩展编排流程**

在调用模型前解析附件和知识空间。仅搜索已授权的空间 ID，以及属于当前对话的临时向量。在生成引用前重新检查 MySQL 中的发布状态。

- [x] **步骤 4：实现查询 API 和 BFF POST SSE 代理**

公开对话创建/列表/删除、消息历史、运行状态、重新生成和对话流端点。保留 `text/event-stream`、UTF-8、Trace ID、取消和背压语义。

- [x] **步骤 5：运行聚焦测试和完整测试**

在 `agent` 中运行： `mvn test`

在 `smart-boot` 中运行： `mvn -pl smart-ai test`

预期：所有测试通过，且取消操作会留下经过审计的终态。

- [x] **步骤 6：独立提交**

Agent 提交： `feat: stream authorized conversations with attachments`

Boot 提交： `feat: proxy agent conversations and streams`

### 任务 9：前端 AI API 与直传组件

**文件：**
- 创建： `F:/project/gongcheng/smart-web/src/api/ai/conversation-api.js`
- 创建： `F:/project/gongcheng/smart-web/src/api/ai/chat-api.js`
- 创建： `F:/project/gongcheng/smart-web/src/api/ai/attachment-api.js`
- 创建： `F:/project/gongcheng/smart-web/src/api/ai/knowledge-api.js`
- 创建： `F:/project/gongcheng/smart-web/src/api/ai/role-grant-api.js`
- 创建： `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/index.vue`
- 创建： `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/oss-direct-upload.js`
- 创建： `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/attachment-validator.js`
- 创建： `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/attachment-status.js`
- 修改： `F:/project/gongcheng/smart-web/package.json`
- 测试： `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/attachment-validator.test.js`
- 测试： `F:/project/gongcheng/smart-web/src/api/ai/chat-api.test.js`

**接口：**
- `streamChat(request, { signal, onEvent })` 增量解析 POST SSE，并保留不完整的 UTF-8 帧。
- `uploadAttachment(file, purpose, hooks)` 执行策略请求、OSS 直传 POST、进度回调、完成校验和取消操作。
- 校验仅限制与类型无关的大小、数量和总字节数。

- [ ] **步骤 1：添加 Vitest 测试依赖和会失败的 API/解析器测试**

将 `vitest`、`jsdom` 和 `@vue/test-utils` 添加为开发依赖，并添加 `test` 脚本。测试拆分的 SSE 帧、多行数据、未知事件容忍、终止行为、50/200 MB 限额、数量上限 10、总量上限 100 MB，以及接受未知扩展名。

- [ ] **步骤 2：运行聚焦测试并确认失败**

运行： `npm test -- src/api/ai/chat-api.test.js src/components/ai/attachment-upload/attachment-validator.test.js`

预期：因模块不存在而测试失败。

- [ ] **步骤 3：实现 API 和流式解析器**

普通请求使用现有 `src/lib/axios.js`。仅对 POST SSE 使用 `fetch`，并传递现有会话工具生成的相同 Authorization、Tenant-Id、Identity-Id 和 Menu-Id 请求头。

- [ ] **步骤 4：实现 OSS 上传编排**

使用 `XMLHttpRequest` 获取上传进度。仅附加签名策略返回的字段和原始文件。成功后调用完成接口，并信任返回的附件记录，而不是构造 `${host}/${key}` 作为凭据。

- [ ] **步骤 5：实现上传界面状态**

支持按钮选择、拖放区域、文档粘贴、图像粘贴、进度、取消、重试、移除、图像缩略图预览、通用文件图标、`UNSUPPORTED` 提示和 `QUARANTINED` 锁定。保持附件磁贴尺寸稳定。

- [ ] **步骤 6：运行测试、代码检查和构建**

运行： `npm test`

运行： `npm run build:test`

预期：测试和生产模式构建均通过。

- [ ] **步骤 7：在 `smart-web` 中提交**

```bash
git add package.json package-lock.json src/api/ai src/components/ai
git commit -m "feat: add direct oss ai attachments"
```

### 任务 10：前端助手工作区

**文件：**
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/index.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/conversation-sidebar.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/chat-header.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/message-list.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/message-item.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/chat-composer.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/citation-list.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/composables/use-chat-stream.js`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/composables/use-conversations.js`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/composables/use-attachments.js`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/assistant/assistant.test.js`

**接口：**
- 动态菜单组件：`ai/assistant/index`；路由：`/ai/assistant`；标题：`智能助手`。
- 编辑器提交内容和已验证为 `READY` 的附件 ID，并在附件校验完成前禁用发送。

- [ ] **步骤 1：编写会失败的组件工作流测试**

测试初始对话加载、新建对话、流式增量、引用渲染、停止、重新生成、附件就绪状态、图像预览、不支持警告、阻止空消息以及移动端侧边栏行为。

- [ ] **步骤 2：运行助手测试并验证失败**

运行： `npm test -- src/views/ai/assistant/assistant.test.js`

预期：组件导入失败。

- [ ] **步骤 3：实现独立工作区**

采用克制的工作界面：桌面端使用固定对话栏，移动端使用抽屉，消息视口灵活伸缩，编辑器固定在内容列内，图标按钮带工具提示，不使用嵌套装饰卡片，页面内不放置功能说明文案。

- [ ] **步骤 4：实现健壮的流状态**

按 `(runId, sequence)` 对事件去重，追加增量时不造成布局偏移，提供稳定的失败操作，保留取消前的部分输出，并在意外断开后查询运行状态而不自动重放提示词。

- [ ] **步骤 5：运行测试和构建**

运行： `npm test -- src/views/ai/assistant/assistant.test.js`

运行： `npm run build:test`

预期：测试和构建均通过。

- [ ] **步骤 6：对桌面端和移动端进行视觉验证**

启动： `npm run localhost`

在 1440x900 和 390x844 下验证：无重叠、消息可读、上传控件可用、编辑器稳定、图像预览可见、引用可展开访问，且停止/重新生成控件正常工作。

- [ ] **步骤 7：在 `smart-web` 中提交**

```bash
git add src/views/ai/assistant
git commit -m "feat: add smart assistant workspace"
```

### 任务 11：前端知识管理与角色授权界面

**文件：**
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/knowledge/index.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/knowledge-space-form.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/document-list.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/document-status.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/role-knowledge-modal.vue`
- 创建： `F:/project/gongcheng/smart-web/src/views/ai/knowledge/knowledge.test.js`
- 修改： `F:/project/gongcheng/smart-web/src/views/system/role/components/role-list/index.vue`

**接口：**
- 知识管理独立于聊天。
- 角色弹窗通过 `/ai/roles/{roleId}/knowledge-grants` 加载和替换授权，且不更改现有角色保存载荷。

- [ ] **步骤 1：检查现有角色列表操作约定**

阅读 `src/views/system/role/components/role-list/index.vue` 及其导入的操作组件。编辑前，在任务进度记录中记下现有操作菜单和权限指令名称。

- [ ] **步骤 2：编写会失败的管理测试**

测试租户/项目表单规则、上传和处理状态、草稿发布、禁用、删除确认、失败后重试、不支持内容的发布锁定、角色授权加载/保存，以及无管理权限时隐藏控件。

- [ ] **步骤 3：运行测试并验证失败**

运行： `npm test -- src/views/ai/knowledge/knowledge.test.js`

预期：组件导入失败。

- [ ] **步骤 4：实现知识管理视图**

采用紧凑且以表格为主的操作布局。显示上传进度、解析器状态、活动版本、发布状态、创建者和更新时间。不得在助手页面提供知识选择器或维护操作。

- [ ] **步骤 5：添加角色操作和隔离的授权弹窗**

按照现有操作菜单和权限约定，添加一个“知识库权限”操作。该弹窗仅编辑 AI 授权，绝不修改角色表单模型。

- [ ] **步骤 6：运行前端验证**

运行： `npm test`

运行： `npm run build:test`

预期：测试和构建均通过。

- [ ] **步骤 7：在 `smart-web` 中提交**

```bash
git add src/views/ai/knowledge src/views/system
git commit -m "feat: manage knowledge and role grants"
```

### 任务 12：只读业务工具适配器与审计

**文件：**
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiProjectToolController.java`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/tool/ProjectOverviewAdapter.java`
- 修改： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/tool/project/ProjectBusinessClient.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/tool/project/SmartBootProjectBusinessClient.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/audit/AuditEvent.java`
- 创建： `F:/project/gongcheng/agent/src/main/java/com/smart/agent/audit/AuditService.java`
- 测试： `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/tool/ProjectOverviewAdapterTest.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/tool/project/SmartBootProjectBusinessClientTest.java`
- 测试： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/audit/AuditServiceTest.java`

**接口：**
- 内部 BFF：`POST /internal/ai/tools/project-overview`。
- Agent 工具保留现有 `project.getOverview` 合约，并在配置后将本地适配器替换为 HTTP 适配器。
- 审计记录对象 ID、操作者、租户、操作、结果、Trace ID 和有界摘要；不记录思维链或凭据。

- [ ] **步骤 1：编写会失败的适配器和审计测试**

测试允许访问的项目、拒绝访问且不调用业务服务的项目、复用当前数据范围、64 KB 结果上限、超时映射、Trace ID 透传，以及凭据和原始模型提示词的脱敏。

- [ ] **步骤 2：运行聚焦测试并验证失败**

在 `smart-boot` 中运行： `mvn -pl smart-ai -Dtest=ProjectOverviewAdapterTest test`

在 `agent` 中运行： `mvn -Dtest=SmartBootProjectBusinessClientTest,AuditServiceTest test`

预期：因缺少适配器而编译失败。

- [ ] **步骤 3：基于现有项目服务实现 Boot 适配器**

在内部复用现有项目 DTO 和权限服务，仅将获准的概览字段映射到 Agent 合约，并在读取数据前立即执行最终的项目/数据范围检查。

- [ ] **步骤 4：实现 Agent HTTP 客户端和审计服务**

仅在本地确定性模式下保留 `LocalProjectBusinessClient`。持久化知识变更、授权替换、运行、取消、工具调用、权限拒绝和失败的审计记录。

- [ ] **步骤 5：运行回归测试**

在 `smart-boot` 中运行： `mvn -pl smart-ai test`

在 `agent` 中运行： `mvn test`

预期：所有测试通过。

- [ ] **步骤 6：独立提交**

Boot 提交： `feat: expose permission checked project tool`

Agent 提交： `feat: call and audit smart boot project tools`

### 任务 13：跨仓库验收与运维文档

**文件：**
- 修改： `F:/project/gongcheng/agent/src/test/java/com/smart/agent/AcceptanceIT.java`
- 创建： `F:/project/gongcheng/agent/docs/runbooks/phase-2-local-integration.md`
- 创建： `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/Phase2BffIT.java`
- 创建： `F:/project/gongcheng/smart-web/playwright-tests/ai-assistant.spec.js`
- 修改： `F:/project/gongcheng/agent/.env.example`
- 修改： `F:/project/gongcheng/smart-boot/smart-app/src/main/resources/application-dev.yml`

**接口：**
- 产出适用于 MySQL、Redis、Qdrant、OSS、`smart-agent`、`smart-boot` 和 `smart-web` 的可重复本地验证路径。

- [ ] **步骤 1：添加会失败的验收场景**

覆盖直传策略和已验证完成流程、对话附件检索、已发布知识引用、排除未发布内容、新请求中的角色变更、项目访问拒绝、SSE 取消、不支持的上传、清理，以及按 Trace ID 查询审计记录。

- [ ] **步骤 2：完成接线前运行各测试套件**

在 `agent` 中运行： `mvn verify`

在 `smart-boot` 中运行： `mvn -pl smart-ai -am verify`

在 `smart-web` 中运行： `npm test && npm run build:test`

预期：新的验收测试暴露所有剩余配置或合约缺口。

- [ ] **步骤 3：完成环境接线和示例配置**

记录 Agent URL、上下文签名密钥、OSS 端点/存储桶/访问凭据、Qdrant 端点/API 密钥、上传限额、保留策略、解析器限额、模型模式和内部超时的变量名及非敏感示例。所有密钥均使用环境变量占位符。

- [ ] **步骤 4：编写运行手册**

记录启动顺序、IDEA 模块、健康检查、OSS CORS 要求、Qdrant 集合检查、手动上传/对话/知识冒烟测试、清理验证、常见错误码，以及各仓库的回滚方法。

- [ ] **步骤 5：运行完整验证**

在 `agent` 中运行： `mvn verify`

在 `smart-boot` 中运行： `mvn -pl smart-ai -am verify`

在 `smart-web` 中运行： `npm test`

在 `smart-web` 中运行： `npm run build:test`

针对已启动的整套系统，在桌面端 1440x900 和移动端 390x844 下运行 Playwright。

预期：所有测试套件通过；浏览器网络检查确认文件字节直接传至 OSS；界面无重叠，控制台无错误。

- [ ] **步骤 6：检查密钥和数据库范围**

在每个仓库中运行：`git diff --check` 和 `git status --short`。

在变更文件中搜索真实凭据，并验证 `smart-boot` 数据库中没有 AI 架构迁移，也没有被修改的业务表。

- [ ] **步骤 7：在各仓库中提交文档和验收测试**

Agent 提交： `test: verify phase 2 agent integration`

Boot 提交： `test: verify phase 2 ai bff`

Web 提交：`test: verify phase 2 assistant workflows`

---

## 最终发布门禁

- [ ] 已观察到每项聚焦测试在实现前失败，并在实现后通过。
- [ ] `smart-agent` 的完整 `mvn verify` 通过，包括基础设施可用时的 MySQL 和 Qdrant 检查。
- [ ] `smart-boot` 的 `mvn -pl smart-ai -am verify` 在 Java 8 下通过。
- [ ] `smart-web` 单元测试和 `npm run build:test` 通过。
- [ ] 桌面端和移动端 Playwright 截图无重叠、空白状态或损坏的预览。
- [ ] 浏览器跟踪证明文件字节直接上传至 OSS。
- [ ] 伪造的租户、用户、角色、项目、知识空间、对象键和 URL 字段均被拒绝。
- [ ] 检索项目知识时必须同时具备角色授权和项目权限。
- [ ] 已禁用、已删除、草稿、不支持和已隔离的文档绝不进入检索。
- [ ] 现有 `smart-boot` 业务表结构保持不变。
- [ ] 未暂存任何密钥或本地 `.env` 文件。
- [ ] 每个仓库均有清晰、可审查的提交序列及已更新的运行手册。
