# Smart Agent Phase 2 Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a role-authorized engineering assistant in `smart-web`, proxied securely through a new `smart-boot/smart-ai` module, with OSS direct upload, independently managed knowledge bases, citations, and audited SSE conversations.

**Architecture:** `smart-web` calls only `smart-boot`, uploads file bytes directly to public-read Alibaba Cloud OSS, and consumes POST SSE with `fetch`. The new Java 8 `smart-ai` BFF derives trusted identity from the existing login, signs internal context, verifies OSS objects, and proxies `smart-agent`; Java 21 `smart-agent` owns all AI data, role-to-knowledge grants, parsing, retrieval, orchestration, and audit records.

**Tech Stack:** Vue 3.4, Ant Design Vue 4.2.1, Pinia, Vite 5, Java 8, Spring Boot 2.7.9, Maven multi-module, Java 21, Spring Boot 3.5.16, LangChain4j 1.19.0, MySQL, Redis, Qdrant 1.18.3 client, Alibaba Cloud OSS.

**Spec:** `docs/superpowers/specs/2026-09-04-smart-agent-phase-2-integration-design.md`

## Global Constraints

- Do not modify existing `smart-boot` business table structures or add fields to existing user and role entities.
- Store all AI domain data, including role-to-knowledge grants, in the `smart-agent` database.
- The browser must never receive the Agent context signing secret or call internal `smart-agent` endpoints directly.
- File bytes must flow from the browser directly to OSS, not through `smart-boot` or `smart-agent`.
- Upload accepts every file extension and MIME type; parsing and publishing remain capability-gated.
- Never execute or unpack archives, scripts, or executable attachments.
- Chat attachments and knowledge documents use separate records, indexes, and retention policies.
- Chat requests accept attachment IDs only, never client-provided OSS URLs or knowledge-space IDs.
- Knowledge access requires tenant equality, a published knowledge space, a role grant, and project permission for project-scoped spaces.
- Qdrant is a rebuildable index; MySQL remains authoritative for document and publication state.
- Preserve Java 8 compatibility in `smart-boot` and Java 21 compatibility in `smart-agent`.
- Use TDD, run focused tests before full tests, and commit each task independently in its owning repository.
- Never commit `.env`, OSS secrets, model keys, passwords, or signing secrets.

---

## File Map

### smart-boot

- `pom.xml`: register the `smart-ai` Maven module.
- `smart-app/pom.xml`: load `smart-ai` into the running application.
- `smart-app/src/main/resources/application-*.yml`: bind non-secret AI endpoint and timeout configuration.
- `smart-ai/pom.xml`: module dependencies and test support.
- `smart-ai/src/main/java/com/smart/ai/config/AiProperties.java`: typed Agent, OSS, timeout, and limit configuration.
- `smart-ai/src/main/java/com/smart/ai/security/AgentContextFactory.java`: derive trusted context from `AuthUtil` and existing authorization services.
- `smart-ai/src/main/java/com/smart/ai/security/AgentContextSigner.java`: create short-lived HMAC context tokens.
- `smart-ai/src/main/java/com/smart/ai/client/SmartAgentClient.java`: JSON request proxy.
- `smart-ai/src/main/java/com/smart/ai/client/SmartAgentSseClient.java`: cancellation-aware SSE proxy.
- `smart-ai/src/main/java/com/smart/ai/oss/AiOssPolicyService.java`: create exact-key, short-lived POST policies.
- `smart-ai/src/main/java/com/smart/ai/oss/AiOssObjectVerifier.java`: verify object metadata after upload.
- `smart-ai/src/main/java/com/smart/ai/controller/*`: public BFF endpoints.
- `smart-ai/src/main/java/com/smart/ai/internal/*`: project permission and read-only tool endpoints.

### smart-agent

- `src/main/resources/db/migration/V5__create_attachment_and_knowledge_management_tables.sql`: attachments, knowledge spaces, versions, grants, citations, and audit schema.
- `src/main/java/com/smart/agent/attachment/*`: upload registration, state machine, validation, and cleanup.
- `src/main/java/com/smart/agent/knowledge/manage/*`: knowledge-space and document lifecycle.
- `src/main/java/com/smart/agent/authorization/*`: role grant resolution and project-scope intersection.
- `src/main/java/com/smart/agent/ingestion/*`: parser routing and asynchronous indexing.
- `src/main/java/com/smart/agent/chat/ChatCommand.java`: attachment IDs only.
- `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`: attachment context and authorized knowledge retrieval.
- `src/main/java/com/smart/agent/chat/ChatController.java`: management-compatible SSE contract.
- `src/main/java/com/smart/agent/audit/*`: security and management audit events.

### smart-web

- `src/api/ai/*`: BFF APIs and POST SSE parser.
- `src/components/ai/attachment-upload/*`: OSS direct upload, validation, progress, cancellation, drag/drop, and paste.
- `src/views/ai/assistant/*`: conversation workspace and citations.
- `src/views/ai/knowledge/*`: independent knowledge management workspace.
- `src/views/system/role/components/role-list/index.vue`: add only a knowledge-permission action and modal trigger.

---

### Task 1: Freeze Cross-Service Contracts and Bootstrap `smart-ai`

**Files:**
- Create: `F:/project/gongcheng/smart-boot/smart-ai/pom.xml`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/config/AiProperties.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/model/AgentContextPayload.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/model/AiErrorResponse.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/config/AiModuleContextTest.java`
- Modify: `F:/project/gongcheng/smart-boot/pom.xml`
- Modify: `F:/project/gongcheng/smart-boot/smart-app/pom.xml`
- Modify: `F:/project/gongcheng/smart-boot/smart-app/src/main/resources/application.yml`

**Interfaces:**
- Produces: `AiProperties`, `AgentContextPayload`, and the stable `AI_*` error envelope used by all later BFF tasks.
- `AgentContextPayload` fields: `tenantId`, `userId`, `identityId`, `roleIds`, `issuedAt`, `expiresAt`, `nonce`.

- [ ] **Step 1: Write the failing module context test**

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

- [ ] **Step 2: Run the focused test and confirm it fails because `smart-ai` is absent**

Run: `mvn -pl smart-ai -am -Dtest=AiModuleContextTest test`

Expected: Maven cannot find the module or test class.

- [ ] **Step 3: Add the module, configuration class, and immutable DTOs**

Use `@ConfigurationProperties(prefix = "smart.ai")` and defaults of 300 seconds for context TTL, 90 seconds for chat timeout, 50 MB for a chat file, 200 MB for a knowledge file, 10 attachments, and 100 MB message total. Keep secrets environment-substituted in application configuration.

- [ ] **Step 4: Run module and application packaging tests**

Run: `mvn -pl smart-ai -am test`

Run: `mvn -pl smart-app -am -DskipTests package`

Expected: both commands succeed under Java 8.

- [ ] **Step 5: Commit in `smart-boot`**

```bash
git add pom.xml smart-app/pom.xml smart-app/src/main/resources/application.yml smart-ai
git commit -m "feat: bootstrap smart ai bff module"
```

### Task 2: Trusted Context Signing and Agent HTTP/SSE Clients

**Files:**
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/security/AgentContextFactory.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/security/AgentContextSigner.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/client/SmartAgentClient.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/client/SmartAgentSseClient.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/client/SmartAgentExceptionMapper.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/security/AgentContextSignerTest.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/client/SmartAgentClientTest.java`

**Interfaces:**
- Consumes: existing `AuthUtil.getUserId()` and `AuthUtil.getIdentityId()` plus existing tenant and role services discovered in this task.
- Produces: `String AgentContextSigner.sign(AgentContextPayload payload)` and cancellation-aware Agent client methods that always attach `X-Agent-Context` and `X-Trace-Id`.

- [ ] **Step 1: Add signing contract tests**

Test that the compact token has three dot-separated segments, changes when role IDs change, rejects an empty secret at startup, sorts role IDs before signing, and never serializes a client-supplied tenant or user value.

- [ ] **Step 2: Run the signing test and verify missing implementations fail**

Run: `mvn -pl smart-ai -Dtest=AgentContextSignerTest test`

Expected: compilation fails for missing signer and factory classes.

- [ ] **Step 3: Implement trusted context creation and HMAC-SHA256 signing**

```java
public interface AgentContextSigner {
    String sign(AgentContextPayload payload);
}

public interface AgentContextFactory {
    AgentContextPayload currentContext();
}
```

Reject missing tenant, user, identity, role set, secret, or non-positive TTL with stable internal exceptions.

- [ ] **Step 4: Add WireMock client tests**

Assert JSON proxying, signed headers, Trace ID propagation, non-2xx error mapping, SSE content-type preservation, and downstream cancellation.

- [ ] **Step 5: Implement clients using Spring `RestTemplate` for JSON and `WebClient` for streaming**

Bound connection, response, and total timeouts from `AiProperties`. Never log authorization headers, signed context, response bodies containing secrets, or OSS policies.

- [ ] **Step 6: Run focused and module tests**

Run: `mvn -pl smart-ai -Dtest=AgentContextSignerTest,SmartAgentClientTest test`

Run: `mvn -pl smart-ai test`

Expected: all tests pass.

- [ ] **Step 7: Commit in `smart-boot`**

```bash
git add smart-ai
git commit -m "feat: sign and proxy trusted agent context"
```

### Task 3: Agent Attachment Domain and Persistence

**Files:**
- Create: `F:/project/gongcheng/agent/src/main/resources/db/migration/V5__create_attachment_and_knowledge_management_tables.sql`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/Attachment.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentPurpose.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentStatus.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentRepository.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/JpaAttachmentRepository.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentService.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/attachment/AttachmentServiceTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/AttachmentPersistenceMySqlIT.java`

**Interfaces:**
- Produces: `registerUpload`, `completeUpload`, `markProcessing`, `markReady`, `markUnsupported`, `quarantine`, `fail`, and `expire` transitions.
- `AttachmentPurpose`: `CHAT_ATTACHMENT`, `KNOWLEDGE_DOCUMENT`.
- `AttachmentStatus`: `PENDING_UPLOAD`, `UPLOADED`, `PROCESSING`, `READY`, `FAILED`, `UNSUPPORTED`, `QUARANTINED`, `EXPIRED`.

- [ ] **Step 1: Write state-machine unit tests**

Cover valid transitions, reject `PENDING_UPLOAD -> READY`, make completion idempotent for equal ETag, reject different ETag, enforce tenant/user ownership, and reject expired upload completion.

- [ ] **Step 2: Run the unit test and confirm missing domain types fail**

Run: `mvn -Dtest=AttachmentServiceTest test`

Expected: compilation fails for missing attachment classes.

- [ ] **Step 3: Implement the aggregate and service without OSS dependencies**

```java
public record CompleteUploadCommand(
    UUID attachmentId, String tenantId, String userId,
    String objectKey, long size, String etag, String detectedMediaType) {}
```

Generate object keys server-side using tenant, purpose, UTC date, user, UUID, and a sanitized extension. Never accept an arbitrary object key during completion.

- [ ] **Step 4: Add the V5 migration and MySQL integration test**

Create attachment, knowledge-space, knowledge-document, document-version, role-grant, citation, and audit tables with tenant-leading indexes and uniqueness on `(tenant_id, role_id, knowledge_space_id)` and `(tenant_id, object_key)`.

- [ ] **Step 5: Run attachment tests and non-Docker regression tests**

Run: `mvn -Dtest=AttachmentServiceTest test`

Run: `mvn test`

Expected: all non-Docker tests pass.

- [ ] **Step 6: Run MySQL integration test when Docker is available**

Run: `mvn -Dit.test=AttachmentPersistenceMySqlIT verify`

Expected: migration applies and tenant-scoped constraints pass. If Docker is unavailable, record this single deferred check without marking it passed.

- [ ] **Step 7: Commit in `smart-agent`**

```bash
git add src/main/resources/db/migration/V5__create_attachment_and_knowledge_management_tables.sql src/main/java/com/smart/agent/attachment src/test/java/com/smart/agent/attachment src/test/java/com/smart/agent/AttachmentPersistenceMySqlIT.java
git commit -m "feat: persist managed agent attachments"
```

### Task 4: OSS Policy and Verified Upload Completion

**Files:**
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/oss/AiAttachmentObjectKeyFactory.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/oss/AiOssPolicyService.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/oss/AiOssObjectVerifier.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiAttachmentController.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentController.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/oss/AiOssPolicyServiceTest.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiAttachmentControllerTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/attachment/AttachmentControllerTest.java`

**Interfaces:**
- Public BFF: `POST /ai/attachments/upload-policy`, `POST /ai/attachments/{id}/complete`, `GET /ai/attachments/{id}`, `DELETE /ai/attachments/{id}`.
- Agent internal registration returns `attachmentId`, exact `objectKey`, `purpose`, `expiresAt`, and configured maximum bytes.
- Completion request contains only `etag`; BFF obtains authoritative size, media type, and object key from OSS metadata.

- [ ] **Step 1: Write policy and controller security tests**

Assert five-minute expiry, exact-key condition, size-only limits, no extension/MIME allowlist, unguessable UUID key, permission requirement for knowledge uploads, and rejection when OSS metadata differs from the registered object.

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `mvn -pl smart-ai -Dtest=AiOssPolicyServiceTest,AiAttachmentControllerTest test`

Expected: compilation fails for missing OSS services and controller.

- [ ] **Step 3: Implement registration, policy generation, and metadata verification**

Use existing `smart-boot` OSS configuration where compatible. Keep the policy interface provider-neutral:

```java
public interface AiOssPolicyService {
    DirectUploadPolicy create(String objectKey, long maxBytes, Instant expiresAt);
}

public interface AiOssObjectVerifier {
    VerifiedObject verify(String objectKey, long maxBytes, String expectedEtag);
}
```

- [ ] **Step 4: Implement Agent attachment endpoints and BFF proxy**

Reject client fields named `tenantId`, `userId`, `objectKey`, `knowledgeSpaceIds`, or `ossUrl` using strict request DTO deserialization.

- [ ] **Step 5: Run both repository test suites**

Run in `smart-boot`: `mvn -pl smart-ai test`

Run in `agent`: `mvn -Dtest=AttachmentControllerTest,AttachmentServiceTest test`

Expected: all tests pass.

- [ ] **Step 6: Commit independently**

In `smart-agent`:

```bash
git add src/main/java/com/smart/agent/attachment src/test/java/com/smart/agent/attachment
git commit -m "feat: expose verified attachment lifecycle"
```

In `smart-boot`:

```bash
git add smart-ai
git commit -m "feat: issue verified oss direct uploads"
```

### Task 5: Knowledge Management and Document Versioning

**Files:**
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeSpace.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeScope.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeStatus.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeDocumentVersion.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeManagementService.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/manage/KnowledgeManagementController.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiKnowledgeController.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/knowledge/manage/KnowledgeManagementServiceTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/knowledge/manage/KnowledgeManagementControllerTest.java`

**Interfaces:**
- Produces all knowledge-space and knowledge-document endpoints specified in design section 10.
- `KnowledgeScope`: `TENANT`, `PROJECT`; `PROJECT` requires exactly one project ID.
- A document version can publish only from `DRAFT`; unsupported or quarantined content cannot publish.

- [ ] **Step 1: Write lifecycle and authorization tests**

Cover tenant/project creation validation, upload association, version increment, draft-only publication, atomic replacement of the prior published version, disable-before-delete behavior, tenant isolation, and forbidden non-manager calls.

- [ ] **Step 2: Run the tests and confirm missing management types fail**

Run: `mvn -Dtest=KnowledgeManagementServiceTest,KnowledgeManagementControllerTest test`

Expected: compilation fails.

- [ ] **Step 3: Implement repositories and lifecycle service**

```java
public interface KnowledgeManagementService {
    KnowledgeSpace createSpace(CreateKnowledgeSpaceCommand command, AgentUserContext actor);
    KnowledgeDocumentVersion attachUploadedDocument(UUID spaceId, UUID attachmentId, AgentUserContext actor);
    KnowledgeDocumentVersion publishVersion(UUID versionId, AgentUserContext actor);
    void disableVersion(UUID versionId, AgentUserContext actor);
    void deleteDocument(UUID documentId, AgentUserContext actor);
}
```

Perform status changes transactionally in MySQL. Emit cleanup work only after the authoritative state no longer permits retrieval.

- [ ] **Step 4: Implement internal Agent controller and BFF controller**

Use pagination for space and document lists. Return stable status, parser error code, active version, creator, and timestamps; never return OSS credentials.

- [ ] **Step 5: Run regression suites**

Run in `agent`: `mvn test`

Run in `smart-boot`: `mvn -pl smart-ai test`

Expected: all non-Docker tests pass.

- [ ] **Step 6: Commit in both repositories**

Agent commit: `feat: manage versioned knowledge documents`

Boot commit: `feat: proxy knowledge management APIs`

### Task 6: Parser Routing, Asynchronous Ingestion, and Cleanup

**Files:**
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/ContentTypeDetector.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/DocumentParser.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/DocumentParserRegistry.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/ingestion/DocumentIngestionJob.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/attachment/AttachmentCleanupJob.java`
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/KnowledgeIngestionService.java`
- Modify: `F:/project/gongcheng/agent/pom.xml`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/ingestion/DocumentParserRegistryTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/ingestion/DocumentIngestionJobTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/attachment/AttachmentCleanupJobTest.java`

**Interfaces:**
- `DocumentParser.supports(DetectedContentType)` selects a parser based on detected bytes, never filename alone.
- `DocumentParser.parse(InputStream, ParseLimits)` returns text blocks with page/sheet/section locators.
- Unsupported types transition to `UNSUPPORTED`; suspicious mismatches transition to `QUARANTINED`.

- [ ] **Step 1: Add failing parser routing tests**

Test PDF, DOCX, XLSX, Markdown, TXT, common image detection, unknown binary handling, executable signature quarantine, extension/content mismatch, decompression and page/character limits.

- [ ] **Step 2: Run focused tests and confirm they fail**

Run: `mvn -Dtest=DocumentParserRegistryTest,DocumentIngestionJobTest test`

Expected: compilation fails for missing ingestion classes.

- [ ] **Step 3: Add Apache Tika detection and focused parsers**

Use streaming reads and explicit maximum extracted characters, archive depth zero, embedded-resource extraction disabled, formula execution disabled, and image metadata-only behavior until a configured vision model is available.

- [ ] **Step 4: Connect ingestion to existing chunking, embedding, and `VectorIndex` contracts**

Use separate Qdrant namespaces/collections for published knowledge and expiring conversation attachments. Include tenant, space, project, document, version, publication state, attachment, and expiry payload fields.

- [ ] **Step 5: Implement retention cleanup**

Select expired chat attachments in bounded pages, mark them `EXPIRED`, delete temporary vectors, then delete OSS objects through a provider port. Retries must be idempotent.

- [ ] **Step 6: Run unit and Qdrant tests**

Run: `mvn test`

Run when Qdrant is reachable: `mvn -Dit.test=ExternalQdrantVectorIndexIT verify`

Expected: unit tests pass; external integration proves tenant and index separation.

- [ ] **Step 7: Commit in `smart-agent`**

```bash
git add pom.xml src/main/java/com/smart/agent/ingestion src/main/java/com/smart/agent/attachment src/main/java/com/smart/agent/knowledge src/test/java/com/smart/agent/ingestion src/test/java/com/smart/agent/attachment
git commit -m "feat: ingest and clean managed documents"
```

### Task 7: Role Grants and Project Permission Intersection

**Files:**
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/RoleKnowledgeGrant.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/RoleKnowledgeGrantService.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/KnowledgeAccessResolver.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/ProjectPermissionClient.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/authorization/RoleKnowledgeGrantController.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiRoleKnowledgeController.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiProjectPermissionController.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/authorization/KnowledgeAccessResolverTest.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/internal/AiProjectPermissionControllerTest.java`

**Interfaces:**
- Public BFF: `GET` and `PUT /ai/roles/{roleId}/knowledge-grants`.
- Internal permission check: `POST /internal/ai/permissions/projects/check` returns only the requested project IDs currently visible to the trusted user.
- `KnowledgeAccessResolver.resolve(AgentUserContext)` returns published tenant spaces plus project spaces passing the permission intersection.

- [ ] **Step 1: Write failing authorization tests**

Test role union, no-role denial, tenant isolation, unpublished space exclusion, project permission intersection, stale deleted role grants, unauthorized grant administration, and immediate effect for a new request.

- [ ] **Step 2: Run focused tests and verify failure**

Run in `agent`: `mvn -Dtest=KnowledgeAccessResolverTest test`

Run in `smart-boot`: `mvn -pl smart-ai -Dtest=AiProjectPermissionControllerTest test`

Expected: missing types or endpoints cause failure.

- [ ] **Step 3: Implement grant replacement and access resolution**

```java
public Set<UUID> replaceGrants(
    String tenantId, String roleId, Set<UUID> knowledgeSpaceIds, AgentUserContext actor);

public Set<AuthorizedKnowledgeSpace> resolve(AgentUserContext context);
```

Validate every target space belongs to the tenant. Replace grants transactionally and audit the before/after set.

- [ ] **Step 4: Implement project permission adapter without copying business rules**

Locate and call the existing project data-scope service inside `smart-boot`. The adapter must return an intersection only; it must not implement a second project-permission algorithm.

- [ ] **Step 5: Run module and Agent regression tests**

Run: `mvn -pl smart-ai test`

Run: `mvn test`

Expected: all non-Docker tests pass.

- [ ] **Step 6: Commit independently**

Agent commit: `feat: authorize knowledge by role and project`

Boot commit: `feat: expose trusted project permission checks`

### Task 8: Extend Conversation Orchestration and SSE

**Files:**
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatCommand.java`
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatEvent.java`
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ChatController.java`
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/knowledge/KnowledgeSearchService.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/chat/ConversationQueryController.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiConversationController.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiChatController.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/chat/AuthorizedChatControllerIT.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/controller/AiChatControllerTest.java`

**Interfaces:**
- Chat accepts `conversationId`, `content`, and up to ten `attachmentIds`; unknown JSON properties fail validation.
- Emits `run.started`, `message.accepted`, `attachment.processing`, `retrieval.started`, `citation`, `tool.started`, `tool.completed`, `answer.delta`, `answer.completed`, and `run.failed`.
- Every event contains `runId` and monotonic `sequence`.

- [ ] **Step 1: Write failing end-to-end chat contract tests**

Cover authorized knowledge retrieval, project denial, ready attachment context, attachment ownership denial, unsupported attachment rejection, citation-before-completion, sequence monotonicity, cancellation to `CANCELLED`, no automatic replay, and regeneration creating a new run.

- [ ] **Step 2: Run Agent chat tests and confirm new cases fail**

Run: `mvn -Dtest=AuthorizedChatControllerIT,ChatControllerIT test`

Expected: tests fail because attachment and authorized-space integration is absent.

- [ ] **Step 3: Extend orchestration with explicit limits and authority checks**

Resolve attachments and knowledge spaces before model invocation. Search only authorized space IDs and temporary vectors belonging to the current conversation. Recheck MySQL publication state before producing citations.

- [ ] **Step 4: Implement query APIs and BFF POST SSE proxy**

Expose create/list/delete conversation, message history, run status, regeneration, and chat stream endpoints. Preserve `text/event-stream`, UTF-8, Trace ID, cancellation, and backpressure.

- [ ] **Step 5: Run focused and full tests**

Run in `agent`: `mvn test`

Run in `smart-boot`: `mvn -pl smart-ai test`

Expected: all tests pass and cancellation leaves an audited terminal state.

- [ ] **Step 6: Commit independently**

Agent commit: `feat: stream authorized conversations with attachments`

Boot commit: `feat: proxy agent conversations and streams`

### Task 9: Frontend AI APIs and Direct Upload Component

**Files:**
- Create: `F:/project/gongcheng/smart-web/src/api/ai/conversation-api.js`
- Create: `F:/project/gongcheng/smart-web/src/api/ai/chat-api.js`
- Create: `F:/project/gongcheng/smart-web/src/api/ai/attachment-api.js`
- Create: `F:/project/gongcheng/smart-web/src/api/ai/knowledge-api.js`
- Create: `F:/project/gongcheng/smart-web/src/api/ai/role-grant-api.js`
- Create: `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/index.vue`
- Create: `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/oss-direct-upload.js`
- Create: `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/attachment-validator.js`
- Create: `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/attachment-status.js`
- Modify: `F:/project/gongcheng/smart-web/package.json`
- Test: `F:/project/gongcheng/smart-web/src/components/ai/attachment-upload/attachment-validator.test.js`
- Test: `F:/project/gongcheng/smart-web/src/api/ai/chat-api.test.js`

**Interfaces:**
- `streamChat(request, { signal, onEvent })` parses POST SSE incrementally and preserves partial UTF-8 frames.
- `uploadAttachment(file, purpose, hooks)` executes policy request, direct OSS POST, progress callbacks, completion verification, and cancellation.
- Validation limits type-independent size, count, and total bytes only.

- [ ] **Step 1: Add Vitest test dependencies and failing API/parser tests**

Add `vitest`, `jsdom`, and `@vue/test-utils` as dev dependencies plus a `test` script. Test split SSE frames, multiline data, unknown event tolerance, abort behavior, 50/200 MB limits, count 10, total 100 MB, and acceptance of unknown extensions.

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `npm test -- src/api/ai/chat-api.test.js src/components/ai/attachment-upload/attachment-validator.test.js`

Expected: tests fail because the modules do not exist.

- [ ] **Step 3: Implement APIs and streaming parser**

Use the existing `src/lib/axios.js` for normal requests. Use `fetch` only for POST SSE and pass the same Authorization, Tenant-Id, Identity-Id, and Menu-Id headers generated by existing session utilities.

- [ ] **Step 4: Implement OSS upload orchestration**

Use `XMLHttpRequest` for upload progress. Append only fields returned by the signed policy and the exact file. On success, call completion and trust the returned attachment record rather than constructing `${host}/${key}` as proof.

- [ ] **Step 5: Implement upload UI states**

Support button selection, drop zone, document paste, image paste, progress, cancel, retry, remove, image thumbnail preview, generic file icon, `UNSUPPORTED` notice, and `QUARANTINED` lockout. Keep the attachment tile dimensions stable.

- [ ] **Step 6: Run tests, lint, and build**

Run: `npm test`

Run: `npm run build:test`

Expected: tests and production-style build pass.

- [ ] **Step 7: Commit in `smart-web`**

```bash
git add package.json package-lock.json src/api/ai src/components/ai
git commit -m "feat: add direct oss ai attachments"
```

### Task 10: Frontend Assistant Workspace

**Files:**
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/index.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/conversation-sidebar.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/chat-header.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/message-list.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/message-item.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/chat-composer.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/components/citation-list.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/composables/use-chat-stream.js`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/composables/use-conversations.js`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/composables/use-attachments.js`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/assistant/assistant.test.js`

**Interfaces:**
- Dynamic menu component: `ai/assistant/index`; route: `/ai/assistant`; title: `智能助手`.
- Composer submits content plus verified `READY` attachment IDs and disables send while attachment validation is incomplete.

- [ ] **Step 1: Write failing component workflow tests**

Test initial conversation load, new conversation, streaming deltas, citation rendering, stop, regenerate, attachment readiness, image preview, unsupported warning, empty-message prevention, and mobile sidebar behavior.

- [ ] **Step 2: Run the assistant test and verify failure**

Run: `npm test -- src/views/ai/assistant/assistant.test.js`

Expected: component import fails.

- [ ] **Step 3: Implement the standalone workspace**

Use a restrained work interface: fixed conversation rail on desktop, drawer on mobile, flexible message viewport, composer fixed within the content column, icon buttons with tooltips, no nested decorative cards, and no feature-explanation copy inside the page.

- [ ] **Step 4: Implement resilient stream state**

Deduplicate events by `(runId, sequence)`, append deltas without layout shifts, show stable failure actions, retain partial cancelled output, and query run state after an unexpected disconnect without automatically replaying the prompt.

- [ ] **Step 5: Run tests and build**

Run: `npm test -- src/views/ai/assistant/assistant.test.js`

Run: `npm run build:test`

Expected: tests and build pass.

- [ ] **Step 6: Visually verify desktop and mobile**

Start: `npm run localhost`

Verify at 1440x900 and 390x844: no overlap, readable messages, usable upload controls, stable composer, visible image previews, accessible citation expansion, and working stop/regenerate controls.

- [ ] **Step 7: Commit in `smart-web`**

```bash
git add src/views/ai/assistant
git commit -m "feat: add smart assistant workspace"
```

### Task 11: Frontend Knowledge Management and Role Grant UI

**Files:**
- Create: `F:/project/gongcheng/smart-web/src/views/ai/knowledge/index.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/knowledge-space-form.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/document-list.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/document-status.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/knowledge/components/role-knowledge-modal.vue`
- Create: `F:/project/gongcheng/smart-web/src/views/ai/knowledge/knowledge.test.js`
- Modify: `F:/project/gongcheng/smart-web/src/views/system/role/components/role-list/index.vue`

**Interfaces:**
- Knowledge management is independent from chat.
- Role modal loads and replaces grants through `/ai/roles/{roleId}/knowledge-grants` without changing the existing role-save payload.

- [ ] **Step 1: Inspect the existing role-list action conventions**

Read `src/views/system/role/components/role-list/index.vue` and its imported action components. Record the existing action-menu and permission directive names in the task progress note before editing.

- [ ] **Step 2: Write failing management tests**

Test tenant/project form rules, upload and processing statuses, draft publication, disable, delete confirmation, retry after failure, unsupported publish lockout, role grant load/save, and hidden controls without management permission.

- [ ] **Step 3: Run the test and verify failure**

Run: `npm test -- src/views/ai/knowledge/knowledge.test.js`

Expected: component import fails.

- [ ] **Step 4: Implement knowledge management views**

Use a compact table-first operations layout. Show upload progress, parser state, active version, publish state, creator, and update time. Do not expose a knowledge selector or maintenance action in the assistant page.

- [ ] **Step 5: Add role action and isolated authorization modal**

Add one “知识库权限” action using the existing action-menu and permission conventions. The modal edits only AI grants and never mutates the role form model.

- [ ] **Step 6: Run frontend verification**

Run: `npm test`

Run: `npm run build:test`

Expected: tests and build pass.

- [ ] **Step 7: Commit in `smart-web`**

```bash
git add src/views/ai/knowledge src/views/system
git commit -m "feat: manage knowledge and role grants"
```

### Task 12: Read-Only Business Tool Adapter and Audit

**Files:**
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/internal/AiProjectToolController.java`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/main/java/com/smart/ai/tool/ProjectOverviewAdapter.java`
- Modify: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/tool/project/ProjectBusinessClient.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/tool/project/SmartBootProjectBusinessClient.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/audit/AuditEvent.java`
- Create: `F:/project/gongcheng/agent/src/main/java/com/smart/agent/audit/AuditService.java`
- Test: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/tool/ProjectOverviewAdapterTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/tool/project/SmartBootProjectBusinessClientTest.java`
- Test: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/audit/AuditServiceTest.java`

**Interfaces:**
- Internal BFF: `POST /internal/ai/tools/project-overview`.
- Agent tool retains existing `project.getOverview` contract and swaps the local adapter for HTTP when configured.
- Audit records object IDs, actor, tenant, action, outcome, Trace ID, and bounded summaries; no chain-of-thought or credentials.

- [ ] **Step 1: Write failing adapter and audit tests**

Test allowed project, denied project with no business-service call, current data-scope reuse, 64 KB result cap, timeout mapping, Trace ID propagation, and redaction of credentials and raw model prompts.

- [ ] **Step 2: Run focused tests and verify failure**

Run in `smart-boot`: `mvn -pl smart-ai -Dtest=ProjectOverviewAdapterTest test`

Run in `agent`: `mvn -Dtest=SmartBootProjectBusinessClientTest,AuditServiceTest test`

Expected: missing adapters cause compilation failure.

- [ ] **Step 3: Implement the Boot adapter against existing project services**

Reuse existing project DTOs and permission services internally, map only the approved overview fields to the Agent contract, and apply final project/data-scope checks immediately before reading data.

- [ ] **Step 4: Implement Agent HTTP client and audit service**

Keep `LocalProjectBusinessClient` available only for local deterministic mode. Persist audit records for knowledge changes, grant replacement, runs, cancellations, tool calls, permission denials, and failures.

- [ ] **Step 5: Run regression tests**

Run in `smart-boot`: `mvn -pl smart-ai test`

Run in `agent`: `mvn test`

Expected: all tests pass.

- [ ] **Step 6: Commit independently**

Boot commit: `feat: expose permission checked project tool`

Agent commit: `feat: call and audit smart boot project tools`

### Task 13: Cross-Repository Acceptance and Operations Documentation

**Files:**
- Modify: `F:/project/gongcheng/agent/src/test/java/com/smart/agent/AcceptanceIT.java`
- Create: `F:/project/gongcheng/agent/docs/runbooks/phase-2-local-integration.md`
- Create: `F:/project/gongcheng/smart-boot/smart-ai/src/test/java/com/smart/ai/Phase2BffIT.java`
- Create: `F:/project/gongcheng/smart-web/playwright-tests/ai-assistant.spec.js`
- Modify: `F:/project/gongcheng/agent/.env.example`
- Modify: `F:/project/gongcheng/smart-boot/smart-app/src/main/resources/application-dev.yml`

**Interfaces:**
- Produces a repeatable local verification path for MySQL, Redis, Qdrant, OSS, `smart-agent`, `smart-boot`, and `smart-web`.

- [ ] **Step 1: Add failing acceptance scenarios**

Cover direct-upload policy and verified completion, chat attachment retrieval, published knowledge citation, unpublished exclusion, role change on a new request, project denial, SSE cancellation, unsupported upload, cleanup, and audit lookup by Trace ID.

- [ ] **Step 2: Run each suite before completing wiring**

Run in `agent`: `mvn verify`

Run in `smart-boot`: `mvn -pl smart-ai -am verify`

Run in `smart-web`: `npm test && npm run build:test`

Expected: new acceptance tests expose any remaining configuration or contract gaps.

- [ ] **Step 3: Complete environment wiring and example configuration**

Document variable names and non-secret examples for Agent URL, context signing secret, OSS endpoint/bucket/access credentials, Qdrant endpoint/API key, upload limits, retention, parser limits, model mode, and internal timeouts. Use environment placeholders for every secret.

- [ ] **Step 4: Write the runbook**

Document startup order, IDEA modules, health checks, OSS CORS requirements, Qdrant collection checks, a manual upload/chat/knowledge smoke test, cleanup verification, common error codes, and rollback per repository.

- [ ] **Step 5: Run complete verification**

Run in `agent`: `mvn verify`

Run in `smart-boot`: `mvn -pl smart-ai -am verify`

Run in `smart-web`: `npm test`

Run in `smart-web`: `npm run build:test`

Run Playwright at desktop 1440x900 and mobile 390x844 against the started stack.

Expected: all suites pass; browser network inspection confirms file bytes go directly to OSS; no UI overlap or console errors occur.

- [ ] **Step 6: Inspect secrets and database scope**

Run in each repository: `git diff --check` and `git status --short`.

Search changed files for real credentials and verify the `smart-boot` database has no AI schema migration or altered business table.

- [ ] **Step 7: Commit documentation and acceptance tests in each repository**

Agent commit: `test: verify phase 2 agent integration`

Boot commit: `test: verify phase 2 ai bff`

Web commit: `test: verify phase 2 assistant workflows`

---

## Final Release Gate

- [ ] Every focused test was observed failing before its implementation and passing afterward.
- [ ] `smart-agent` full `mvn verify` passes, including MySQL and Qdrant checks where infrastructure is available.
- [ ] `smart-boot` `mvn -pl smart-ai -am verify` passes under Java 8.
- [ ] `smart-web` unit tests and `npm run build:test` pass.
- [ ] Desktop and mobile Playwright screenshots show no overlap, blank state, or broken preview.
- [ ] A browser trace proves file bytes upload directly to OSS.
- [ ] Forged tenant, user, role, project, knowledge-space, object-key, and URL fields are rejected.
- [ ] Role grants and project permissions are both required for project knowledge retrieval.
- [ ] Disabled, deleted, draft, unsupported, and quarantined documents never enter retrieval.
- [ ] Existing `smart-boot` business table structures remain unchanged.
- [ ] No secret or local `.env` file is staged.
- [ ] Each repository has a clean, reviewable commit series and updated runbook.
