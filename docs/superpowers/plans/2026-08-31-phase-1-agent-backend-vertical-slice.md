# Phase 1 Agent Backend Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-shaped `smart-agent` backend vertical slice that starts independently, persists conversations and audit runs in MySQL, accepts a trusted engineering-system user context, executes one read-only project tool, streams answers over SSE, indexes text knowledge in Qdrant, and returns document citations.

**Architecture:** The repository contains one Java 21 Spring Boot application organized by feature packages. Domain ports isolate model, business-tool, vector-store, and identity dependencies; local deterministic adapters make the entire vertical slice testable without external model or `smart-boot`, while profile-based production adapters connect to OpenAI-compatible models, Qdrant, and the future `smart-boot` AI adapter.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Maven, LangChain4j 1.19.0, MySQL 8.x, Flyway, Redis, Qdrant 1.18.2 with official Java client 1.18.3, JUnit 5, AssertJ, Mockito, Testcontainers, WireMock.

**Spec:** `docs/superpowers/specs/2026-08-31-smart-agent-system-design.md`

## Global Constraints

- `smart-agent` runs on Java 21 and remains a separate service from Java 8 `smart-boot`.
- The application targets only the existing engineering management system; do not add generic multi-platform abstractions or a `platform_id` column.
- All persisted tenant-owned records include `tenant_id`; project-scoped records additionally include `project_id` where applicable.
- The model never receives or chooses trusted tenant, user, role, or project permission claims; these arrive through verified server context.
- Business data is accessed only through typed tools; no arbitrary production SQL endpoint is included in this plan.
- Phase 1 tools are read-only and execute automatically only after both agent-side and business-side permission validation.
- MySQL is the metadata source of truth. Qdrant is a rebuildable search index.
- Qdrant queries must filter by `tenant_id`, permitted `space_id`, published status, and project scope before similarity ranking.
- Responses distinguish business facts, document evidence, and model analysis and expose citations.
- Never log API keys, access tokens, full prompts, complete tool results, or document contents.
- Every task follows test-driven development and ends with an independently reviewable commit.

## Scope Decomposition

This plan implements only the first backend vertical slice inside the `agent` repository. The following are separate follow-up plans after this slice passes acceptance:

1. `smart-boot` production identity exchange and AI tool adapter.
2. `smart-web` unified chat center and page-side assistant.
3. Full document upload, PDF/Word/Excel parsing, MinIO synchronization, and OCR.
4. Additional contract, schedule, budget, finance, bidding, archive, and report capabilities.
5. Human approval and resumable write operations.

## Planned File Map

```text
agent/
├─ pom.xml                                      build and dependency versions
├─ compose.dev.yml                              local MySQL, Redis and Qdrant
├─ .env.example                                 non-secret local variables
├─ src/main/java/com/smart/agent/
│  ├─ SmartAgentApplication.java                Spring Boot entry point
│  ├─ common/
│  │  ├─ api/ApiError.java                      stable API error payload
│  │  ├─ error/AgentException.java              typed domain error
│  │  └─ trace/TraceIdFilter.java               trace identifier propagation
│  ├─ security/
│  │  ├─ AgentUserContext.java                  trusted engineering user context
│  │  ├─ AgentContextHolder.java                request-scoped context access
│  │  ├─ ContextTokenVerifier.java              token verification port
│  │  ├─ LocalContextTokenVerifier.java         local/test signed-token adapter
│  │  └─ AgentContextFilter.java                request authentication filter
│  ├─ conversation/
│  │  ├─ Conversation.java                      conversation aggregate
│  │  ├─ Message.java                           persisted message model
│  │  ├─ ConversationRepository.java            persistence port
│  │  ├─ JpaConversationRepository.java         MySQL adapter
│  │  └─ ConversationService.java               conversation use cases
│  ├─ run/
│  │  ├─ AgentRun.java                          durable run state
│  │  ├─ AgentRunStatus.java                    state enum
│  │  ├─ AgentRunRepository.java                run persistence port
│  │  └─ AgentRunService.java                   transition rules and audit
│  ├─ tool/
│  │  ├─ AgentTool.java                         typed tool contract
│  │  ├─ ToolRisk.java                          L0-L3 risk classification
│  │  ├─ ToolContext.java                       non-model trusted context
│  │  ├─ ToolRegistry.java                      permission-aware tool discovery
│  │  ├─ ToolExecutor.java                      validated invocation
│  │  └─ project/
│  │     ├─ ProjectOverviewInput.java
│  │     ├─ ProjectOverviewResult.java
│  │     ├─ ProjectBusinessClient.java           smart-boot adapter port
│  │     └─ ProjectOverviewTool.java             first L1 tool
│  ├─ model/
│  │  ├─ ModelGateway.java                      model-independent streaming port
│  │  ├─ ModelRequest.java
│  │  ├─ ModelEvent.java
│  │  ├─ LocalDeterministicModelGateway.java     tests and local smoke use
│  │  └─ OpenAiCompatibleModelGateway.java       configurable real adapter
│  ├─ knowledge/
│  │  ├─ KnowledgeDocument.java                 metadata aggregate
│  │  ├─ KnowledgeChunk.java                    chunk metadata
│  │  ├─ KnowledgeRepository.java               MySQL metadata port
│  │  ├─ IngestTextCommand.java                 validated text-ingestion input
│  │  ├─ EmbeddingGateway.java                  embedding port
│  │  ├─ VectorIndex.java                       vector search port
│  │  ├─ IndexedChunk.java                      vector upsert contract
│  │  ├─ VectorSearchQuery.java                 trusted filtered-search contract
│  │  ├─ VectorHit.java                         vector search result
│  │  ├─ QdrantVectorIndex.java                 Qdrant adapter
│  │  ├─ KnowledgeIngestionService.java         text ingestion
│  │  └─ KnowledgeSearchService.java            ACL-filtered retrieval
│  └─ chat/
│     ├─ ChatCommand.java                       input contract
│     ├─ ChatEvent.java                         SSE event contract
│     ├─ ChatOrchestrator.java                  vertical-slice coordinator
│     └─ ChatController.java                    REST/SSE API
├─ src/main/resources/
│  ├─ application.yml
│  ├─ application-local.yml
│  └─ db/migration/
│     ├─ V1__create_conversation_and_run_tables.sql
│     └─ V2__create_knowledge_tables.sql
└─ src/test/java/com/smart/agent/
   ├─ ArchitectureTest.java
   ├─ security/AgentContextFilterTest.java
   ├─ conversation/ConversationServiceTest.java
   ├─ run/AgentRunServiceTest.java
   ├─ tool/ToolExecutorTest.java
   ├─ model/ModelGatewayContractTest.java
   ├─ knowledge/KnowledgeSearchServiceTest.java
   ├─ knowledge/QdrantVectorIndexIT.java
   └─ chat/ChatControllerIT.java
```

---

### Task 1: Bootstrap the Java 21 service and local infrastructure

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `.env.example`
- Create: `compose.dev.yml`
- Create: `src/main/java/com/smart/agent/SmartAgentApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-local.yml`
- Create: `src/test/java/com/smart/agent/SmartAgentApplicationTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: none.
- Produces: executable Spring Boot application; local services at MySQL `3307`, Redis `6380`, Qdrant HTTP `6333` and gRPC `6334`; Maven commands used by every later task.

- [ ] **Step 1: Write the failing context test**

```java
package com.smart.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class SmartAgentApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the test and verify bootstrap is absent**

Run: `mvn -q -Dtest=SmartAgentApplicationTest test`

Expected: FAIL because `pom.xml` and `SmartAgentApplication` do not exist.

- [ ] **Step 3: Create the Maven build and application entry point**

Use Spring Boot parent `3.5.16`, set `java.version` to `21`, and add these dependencies:

```xml
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webflux</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
  <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>dev.langchain4j</groupId><artifactId>langchain4j</artifactId><version>1.19.0</version></dependency>
  <dependency><groupId>dev.langchain4j</groupId><artifactId>langchain4j-open-ai</artifactId><version>1.19.0</version></dependency>
  <dependency><groupId>io.qdrant</groupId><artifactId>client</artifactId><version>1.18.3</version></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.wiremock</groupId><artifactId>wiremock-standalone</artifactId><version>3.13.1</version><scope>test</scope></dependency>
</dependencies>
```

Create the entry point:

```java
package com.smart.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartAgentApplication.class, args);
    }
}
```

- [ ] **Step 4: Add safe externalized configuration**

`application.yml` must read secrets only from environment variables:

```yaml
spring:
  application:
    name: smart-agent
  profiles:
    default: local
  datasource:
    url: ${AGENT_DB_URL:jdbc:mysql://localhost:3307/smart_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${AGENT_DB_USERNAME:smart_agent}
    password: ${AGENT_DB_PASSWORD:smart_agent_dev}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  data:
    redis:
      host: ${AGENT_REDIS_HOST:localhost}
      port: ${AGENT_REDIS_PORT:6380}
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
agent:
  qdrant:
    host: ${AGENT_QDRANT_HOST:localhost}
    grpc-port: ${AGENT_QDRANT_GRPC_PORT:6334}
    api-key: ${AGENT_QDRANT_API_KEY:}
  model:
    mode: ${AGENT_MODEL_MODE:local}
    base-url: ${AGENT_MODEL_BASE_URL:http://localhost:11434/v1}
    api-key: ${AGENT_MODEL_API_KEY:local-development-key}
    chat-model: ${AGENT_CHAT_MODEL:local-test-model}
```

`compose.dev.yml` must pin `mysql:8.4.6`, `redis:7.4.5-alpine`, and `qdrant/qdrant:v1.18.2`, use named volumes, and bind ports only to `127.0.0.1`.

- [ ] **Step 5: Run the test and package**

Run: `mvn -q test`

Expected: PASS.

Run: `mvn -q -DskipTests package`

Expected: `target/smart-agent-*.jar` exists.

- [ ] **Step 6: Commit the bootstrap**

```bash
git add pom.xml .gitignore .env.example compose.dev.yml README.md src
git commit -m "build: bootstrap smart agent service"
```

### Task 2: Create durable conversation and run persistence

**Files:**
- Create: `src/main/resources/db/migration/V1__create_conversation_and_run_tables.sql`
- Create: `src/main/java/com/smart/agent/conversation/Conversation.java`
- Create: `src/main/java/com/smart/agent/conversation/Message.java`
- Create: `src/main/java/com/smart/agent/conversation/ConversationRepository.java`
- Create: `src/main/java/com/smart/agent/conversation/JpaConversationRepository.java`
- Create: `src/main/java/com/smart/agent/conversation/ConversationService.java`
- Create: `src/main/java/com/smart/agent/run/AgentRunStatus.java`
- Create: `src/main/java/com/smart/agent/run/AgentRun.java`
- Create: `src/main/java/com/smart/agent/run/AgentRunRepository.java`
- Create: `src/main/java/com/smart/agent/run/AgentRunService.java`
- Test: `src/test/java/com/smart/agent/conversation/ConversationServiceTest.java`
- Test: `src/test/java/com/smart/agent/run/AgentRunServiceTest.java`

**Interfaces:**
- Consumes: Spring Data JPA and Flyway from Task 1.
- Produces: `ConversationService.create(String tenantId, String userId, String title)`, `ConversationService.appendMessage(String conversationId, Message.Role role, String content)`, `AgentRunService.start(String tenantId, String userId, String conversationId)`, and `AgentRunService.transition(String runId, AgentRunStatus expected, AgentRunStatus next)`.

- [ ] **Step 1: Write failing aggregate tests**

```java
@Test
void appendsMessagesInSequence() {
    Conversation conversation = Conversation.create("tenant-1", "user-1", "项目问答");
    conversation.append(Message.Role.USER, "项目进度如何");
    conversation.append(Message.Role.ASSISTANT, "正在查询");
    assertThat(conversation.messages()).extracting(Message::sequence).containsExactly(1L, 2L);
}

@Test
void rejectsInvalidRunTransition() {
    AgentRun run = AgentRun.start("tenant-1", "user-1", "conversation-1");
    assertThatThrownBy(() -> run.transition(AgentRunStatus.COMPLETED))
            .isInstanceOf(IllegalStateException.class);
}
```

- [ ] **Step 2: Run tests and verify missing domain types**

Run: `mvn -q -Dtest=ConversationServiceTest,AgentRunServiceTest test`

Expected: FAIL with missing `Conversation` and `AgentRun` symbols.

- [ ] **Step 3: Implement aggregates and transition rules**

Allowed run transitions are exact:

```java
private static final Map<AgentRunStatus, Set<AgentRunStatus>> ALLOWED = Map.of(
    RECEIVED, Set.of(ROUTING, FAILED, CANCELLED),
    ROUTING, Set.of(PLANNING, FAILED, CANCELLED),
    PLANNING, Set.of(TOOL_SELECTING, RETRIEVING, GENERATING, FAILED, CANCELLED),
    TOOL_SELECTING, Set.of(TOOL_EXECUTING, FAILED, CANCELLED),
    TOOL_EXECUTING, Set.of(RETRIEVING, GENERATING, FAILED, TIMEOUT, PERMISSION_DENIED),
    RETRIEVING, Set.of(GENERATING, FAILED, TIMEOUT, PERMISSION_DENIED),
    GENERATING, Set.of(COMPLETED, FAILED, TIMEOUT),
    WAITING_APPROVAL, Set.of(RESUMING, CANCELLED),
    RESUMING, Set.of(TOOL_EXECUTING, FAILED, PERMISSION_DENIED)
);
```

Use UUID strings generated server-side. Store message content as `LONGTEXT`, but do not store model reasoning fields.

- [ ] **Step 4: Create exact Flyway tables**

Create `ai_conversation`, `ai_message`, `ai_run`, and `ai_run_step` with `varchar(36)` IDs, `tenant_id`, audit timestamps, optimistic `version`, and indexes on `(tenant_id,user_id,update_time)` and `(conversation_id,sequence_no)`.

Add foreign keys from messages and runs to conversations. `ai_run_step` stores step type, status, safe input summary, safe output summary, started time, and finished time.

- [ ] **Step 5: Run unit and MySQL migration tests**

Run: `mvn -q -Dtest=ConversationServiceTest,AgentRunServiceTest test`

Expected: PASS.

Run: `docker compose -f compose.dev.yml up -d mysql && mvn -q spring-boot:run -Dspring-boot.run.profiles=local`

Expected: application starts and Flyway reports migration V1 applied. Stop the app after the health check succeeds.

- [ ] **Step 6: Commit persistence**

```bash
git add src/main/java/com/smart/agent/conversation src/main/java/com/smart/agent/run src/main/resources/db/migration src/test
git commit -m "feat: persist conversations and agent runs"
```

### Task 3: Authenticate engineering user context

**Files:**
- Create: `src/main/java/com/smart/agent/security/AgentUserContext.java`
- Create: `src/main/java/com/smart/agent/security/AgentContextHolder.java`
- Create: `src/main/java/com/smart/agent/security/ContextTokenVerifier.java`
- Create: `src/main/java/com/smart/agent/security/LocalContextTokenVerifier.java`
- Create: `src/main/java/com/smart/agent/security/AgentContextFilter.java`
- Create: `src/main/java/com/smart/agent/common/api/ApiError.java`
- Create: `src/main/java/com/smart/agent/common/error/AgentException.java`
- Create: `src/main/java/com/smart/agent/common/trace/TraceIdFilter.java`
- Test: `src/test/java/com/smart/agent/security/AgentContextFilterTest.java`

**Interfaces:**
- Consumes: servlet request lifecycle from Task 1.
- Produces: `ContextTokenVerifier.verify(String token): AgentUserContext`, request attribute `AgentUserContext.class.getName()`, and response header `X-Trace-Id`.

- [ ] **Step 1: Write failing filter tests**

```java
@Test
void rejectsMissingContextToken() throws Exception {
    mvc.perform(get("/internal/test-context"))
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.code").value("AGENT_UNAUTHORIZED"));
}

@Test
void exposesVerifiedContextWithoutTrustingRequestParameters() throws Exception {
    when(verifier.verify("signed-token")).thenReturn(new AgentUserContext(
            "tenant-1", "user-1", "identity-1", Set.of("project:read"), Set.of("project-1")));
    mvc.perform(get("/internal/test-context")
            .header("X-Agent-Context", "signed-token")
            .param("tenantId", "forged-tenant"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.tenantId").value("tenant-1"));
}
```

- [ ] **Step 2: Run tests and verify filter is missing**

Run: `mvn -q -Dtest=AgentContextFilterTest test`

Expected: FAIL because security types do not exist.

- [ ] **Step 3: Implement immutable context and verifier port**

```java
public record AgentUserContext(
        String tenantId,
        String userId,
        String identityId,
        Set<String> permissions,
        Set<String> projectIds) {
    public boolean canAccessProject(String projectId) {
        return projectIds.contains(projectId);
    }
}

public interface ContextTokenVerifier {
    AgentUserContext verify(String token);
}
```

`LocalContextTokenVerifier` is active only under `local` and `test`; it accepts an HMAC-signed compact JSON token using `AGENT_LOCAL_CONTEXT_SECRET`. No endpoint may accept raw tenant or permission headers.

- [ ] **Step 4: Implement filters and stable errors**

`AgentContextFilter` protects `/agent/**`, reads `X-Agent-Context`, calls the verifier, and clears thread-local/request state in `finally`. `TraceIdFilter` accepts a valid 32- or 36-character trace ID or generates a UUID.

- [ ] **Step 5: Run security tests**

Run: `mvn -q -Dtest=AgentContextFilterTest test`

Expected: PASS, including missing, malformed, expired, and forged token cases.

- [ ] **Step 6: Commit security context**

```bash
git add src/main/java/com/smart/agent/security src/main/java/com/smart/agent/common src/test/java/com/smart/agent/security
git commit -m "feat: verify engineering user context"
```

### Task 4: Add permission-aware project tool execution

**Files:**
- Create: `src/main/java/com/smart/agent/tool/AgentTool.java`
- Create: `src/main/java/com/smart/agent/tool/ToolRisk.java`
- Create: `src/main/java/com/smart/agent/tool/ToolContext.java`
- Create: `src/main/java/com/smart/agent/tool/ToolRegistry.java`
- Create: `src/main/java/com/smart/agent/tool/ToolExecutor.java`
- Create: `src/main/java/com/smart/agent/tool/project/ProjectOverviewInput.java`
- Create: `src/main/java/com/smart/agent/tool/project/ProjectOverviewResult.java`
- Create: `src/main/java/com/smart/agent/tool/project/ProjectBusinessClient.java`
- Create: `src/main/java/com/smart/agent/tool/project/LocalProjectBusinessClient.java`
- Create: `src/main/java/com/smart/agent/tool/project/ProjectOverviewTool.java`
- Test: `src/test/java/com/smart/agent/tool/ToolExecutorTest.java`

**Interfaces:**
- Consumes: `AgentUserContext` from Task 3.
- Produces: `AgentTool<I,O>`, `ToolExecutor.execute(String toolKey, Object input, AgentUserContext userContext)`, and tool key `project.getOverview`.

- [ ] **Step 1: Write failing authorization and execution tests**

```java
@Test
void deniesProjectOutsideTrustedScopeBeforeCallingBusinessClient() {
    AgentUserContext user = context(Set.of("project:read"), Set.of("project-1"));
    assertThatThrownBy(() -> executor.execute(
            "project.getOverview", new ProjectOverviewInput("project-2"), user))
        .isInstanceOf(AgentException.class)
        .hasMessageContaining("project scope");
    verifyNoInteractions(projectBusinessClient);
}

@Test
void executesReadOnlyProjectToolWithTrustedToolContext() {
    when(projectBusinessClient.getOverview(any(), eq("project-1")))
        .thenReturn(new ProjectOverviewResult("project-1", "示例项目", "IN_PROGRESS", 0.42));
    Object result = executor.execute("project.getOverview",
        new ProjectOverviewInput("project-1"), context(Set.of("project:read"), Set.of("project-1")));
    assertThat(result).isInstanceOf(ProjectOverviewResult.class);
}
```

- [ ] **Step 2: Run tests and verify tool contracts are missing**

Run: `mvn -q -Dtest=ToolExecutorTest test`

Expected: FAIL with missing tool types.

- [ ] **Step 3: Implement the typed tool contract**

```java
public interface AgentTool<I, O> {
    String key();
    Class<I> inputType();
    String requiredPermission();
    ToolRisk risk();
    O execute(I input, ToolContext context);
}

public enum ToolRisk { L0, L1, L2, L3 }
```

`ToolExecutor` must deserialize through a dedicated Jackson `ObjectMapper`, reject unknown fields, check permission, check project scope, limit serialized result size to 64KB, and record a safe summary through `AgentRunService`.

- [ ] **Step 4: Implement `project.getOverview`**

`ProjectOverviewTool` requires `project:read`, is L1, accepts only `projectId`, and calls `ProjectBusinessClient.getOverview(ToolContext, projectId)`. `LocalProjectBusinessClient` is active only in local/test and returns deterministic fixture data.

- [ ] **Step 5: Run tool tests**

Run: `mvn -q -Dtest=ToolExecutorTest test`

Expected: PASS for valid execution, missing permission, out-of-scope project, unknown tool, unknown input field, timeout, and oversized result.

- [ ] **Step 6: Commit the first tool**

```bash
git add src/main/java/com/smart/agent/tool src/test/java/com/smart/agent/tool
git commit -m "feat: add permission aware project tool"
```

### Task 5: Add model gateway and deterministic orchestration

**Files:**
- Create: `src/main/java/com/smart/agent/model/ModelGateway.java`
- Create: `src/main/java/com/smart/agent/model/ModelRequest.java`
- Create: `src/main/java/com/smart/agent/model/ModelEvent.java`
- Create: `src/main/java/com/smart/agent/model/LocalDeterministicModelGateway.java`
- Create: `src/main/java/com/smart/agent/model/OpenAiCompatibleModelGateway.java`
- Create: `src/test/java/com/smart/agent/model/ModelGatewayContractTest.java`

**Interfaces:**
- Consumes: LangChain4j dependencies from Task 1 and tool keys from Task 4.
- Produces: `ModelGateway.stream(ModelRequest): Flux<ModelEvent>` where events are `TextDelta`, `ToolRequested`, `Completed`, or `Failed`.

- [ ] **Step 1: Write the model gateway contract test**

```java
@Test
void localGatewayEmitsToolRequestThenFinalText() {
    ModelRequest request = ModelRequest.userQuestion(
            "run-1", "查询项目 project-1 概况", List.of("project.getOverview"));
    StepVerifier.create(gateway.stream(request))
        .expectNextMatches(e -> e instanceof ModelEvent.ToolRequested t
                && t.toolKey().equals("project.getOverview"))
        .expectNextMatches(e -> e instanceof ModelEvent.Completed)
        .verifyComplete();
}
```

- [ ] **Step 2: Run test and verify missing gateway**

Run: `mvn -q -Dtest=ModelGatewayContractTest test`

Expected: FAIL with missing gateway types.

- [ ] **Step 3: Implement model-neutral event types**

```java
public sealed interface ModelEvent {
    record TextDelta(String text) implements ModelEvent {}
    record ToolRequested(String callId, String toolKey, String argumentsJson) implements ModelEvent {}
    record Completed(String text, int inputTokens, int outputTokens) implements ModelEvent {}
    record Failed(String code, String message) implements ModelEvent {}
}
```

`ModelRequest` contains run ID, system instruction version, redacted conversation messages, allowed tool specifications, and retrieved evidence. It does not contain trusted claims as prompt text.

- [ ] **Step 4: Implement local and OpenAI-compatible adapters**

`LocalDeterministicModelGateway` recognizes the fixed local phrase and emits a project tool request for integration tests. `OpenAiCompatibleModelGateway` is enabled only when `agent.model.mode=openai-compatible`, uses LangChain4j streaming, has connect/read timeout values, disables request/response body logging, and maps provider errors to stable codes.

- [ ] **Step 5: Run contract tests**

Run: `mvn -q -Dtest=ModelGatewayContractTest test`

Expected: PASS for tool request, text streaming, provider timeout, malformed tool arguments, and token metadata.

- [ ] **Step 6: Commit model gateway**

```bash
git add src/main/java/com/smart/agent/model src/test/java/com/smart/agent/model
git commit -m "feat: add pluggable model gateway"
```

### Task 6: Persist knowledge metadata and build Qdrant indexing

**Files:**
- Create: `src/main/resources/db/migration/V2__create_knowledge_tables.sql`
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeDocument.java`
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeChunk.java`
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeRepository.java`
- Create: `src/main/java/com/smart/agent/knowledge/IngestTextCommand.java`
- Create: `src/main/java/com/smart/agent/knowledge/EmbeddingGateway.java`
- Create: `src/main/java/com/smart/agent/knowledge/LocalHashEmbeddingGateway.java`
- Create: `src/main/java/com/smart/agent/knowledge/VectorIndex.java`
- Create: `src/main/java/com/smart/agent/knowledge/IndexedChunk.java`
- Create: `src/main/java/com/smart/agent/knowledge/VectorSearchQuery.java`
- Create: `src/main/java/com/smart/agent/knowledge/VectorHit.java`
- Create: `src/main/java/com/smart/agent/knowledge/QdrantVectorIndex.java`
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeIngestionService.java`
- Test: `src/test/java/com/smart/agent/knowledge/QdrantVectorIndexIT.java`

**Interfaces:**
- Consumes: Qdrant client and MySQL from Task 1; tenant context from Task 3.
- Produces: `KnowledgeIngestionService.ingestText(IngestTextCommand): String documentId`, `VectorIndex.upsert(List<IndexedChunk>)`, and `VectorIndex.search(VectorSearchQuery): List<VectorHit>`.

- [ ] **Step 1: Write failing Qdrant filter integration test**

```java
@Testcontainers
class QdrantVectorIndexIT {
    @Test
    void searchNeverReturnsAnotherTenantOrUnpublishedChunk() {
        index.upsert(List.of(
            chunk("a", "tenant-1", "space-1", "project-1", "published", vector(1, 0)),
            chunk("b", "tenant-2", "space-1", "project-1", "published", vector(1, 0)),
            chunk("c", "tenant-1", "space-1", "project-1", "draft", vector(1, 0))));
        List<VectorHit> hits = index.search(query("tenant-1", Set.of("space-1"), Set.of("project-1"), vector(1, 0)));
        assertThat(hits).extracting(VectorHit::chunkId).containsExactly("a");
    }
}
```

- [ ] **Step 2: Run test and verify Qdrant adapter is missing**

Run: `mvn -q -Dtest=QdrantVectorIndexIT test`

Expected: FAIL with missing vector types.

- [ ] **Step 3: Create knowledge metadata schema**

V2 creates `ai_knowledge_space`, `ai_document`, `ai_document_version`, `ai_document_acl`, `ai_document_chunk`, and `ai_ingestion_job`. Store source text checksum, parser version, embedding model key, vector point ID, page number, section title, status, tenant ID, optional organization/project IDs, and audit timestamps.

- [ ] **Step 4: Implement deterministic text ingestion**

`KnowledgeIngestionService` normalizes line endings, rejects blank or over-1MB text, computes SHA-256, splits by headings and paragraphs into chunks of at most 1,200 Unicode code points with 150-code-point overlap, persists metadata, generates embeddings, and upserts Qdrant points only after the MySQL transaction commits.

`LocalHashEmbeddingGateway` creates deterministic 64-dimensional normalized vectors for tests only.

- [ ] **Step 5: Implement Qdrant collection and payload indexes**

At startup, create `agent_knowledge_dev` if absent with cosine distance and configured vector dimension. Create keyword payload indexes for `tenant_id`, `space_id`, `project_id`, and `status`. Every query must add `must` filters for tenant and published status and `should/must` scope filters derived from trusted context.

- [ ] **Step 6: Run Qdrant and migration tests**

Run: `mvn -q -Dtest=QdrantVectorIndexIT test`

Expected: PASS for tenant isolation, space filtering, project filtering, published status, top-K limit, deletion, and idempotent reindexing.

- [ ] **Step 7: Commit knowledge indexing**

```bash
git add src/main/java/com/smart/agent/knowledge src/main/resources/db/migration/V2__create_knowledge_tables.sql src/test/java/com/smart/agent/knowledge
git commit -m "feat: index knowledge in qdrant"
```

### Task 7: Add permission-filtered knowledge retrieval and citations

**Files:**
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeSearchService.java`
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeSearchQuery.java`
- Create: `src/main/java/com/smart/agent/knowledge/KnowledgeCitation.java`
- Test: `src/test/java/com/smart/agent/knowledge/KnowledgeSearchServiceTest.java`

**Interfaces:**
- Consumes: `EmbeddingGateway`, `VectorIndex`, `AgentUserContext`, and knowledge metadata from Task 6.
- Produces: `KnowledgeSearchService.search(KnowledgeSearchQuery, AgentUserContext): List<KnowledgeCitation>`.

- [ ] **Step 1: Write failing permission and citation tests**

```java
@Test
void returnsOnlyAllowedPublishedEvidenceWithStableCitationLocation() {
    when(index.search(any())).thenReturn(List.of(new VectorHit(
        "chunk-1", "doc-1", 0.91, "安全检查制度内容", Map.of(
            "tenant_id", "tenant-1", "space_id", "space-1", "project_id", "project-1"))));
    List<KnowledgeCitation> result = service.search(
        new KnowledgeSearchQuery("安全检查要求", Set.of("space-1"), "project-1", 5),
        context("tenant-1", Set.of("project-1")));
    assertThat(result.getFirst().location()).isEqualTo("第3页 / 安全检查");
}
```

- [ ] **Step 2: Run tests and verify search service is missing**

Run: `mvn -q -Dtest=KnowledgeSearchServiceTest test`

Expected: FAIL with missing search types.

- [ ] **Step 3: Implement retrieval rules**

Reject empty query, inaccessible project, empty allowed spaces, `topK < 1`, or `topK > 20`. Embed the query, search Qdrant with trusted filters, load authoritative document metadata from MySQL, discard stale vector hits, and return immutable citations containing document ID, version, title, page, section, excerpt, score, and an opaque citation token.

- [ ] **Step 4: Run retrieval tests**

Run: `mvn -q -Dtest=KnowledgeSearchServiceTest test`

Expected: PASS for tenant isolation, stale vector rejection, deleted document rejection, project access, citation formatting, and maximum result count.

- [ ] **Step 5: Commit retrieval**

```bash
git add src/main/java/com/smart/agent/knowledge src/test/java/com/smart/agent/knowledge
git commit -m "feat: retrieve permission filtered knowledge"
```

### Task 8: Orchestrate chat and expose SSE events

**Files:**
- Create: `src/main/java/com/smart/agent/chat/ChatCommand.java`
- Create: `src/main/java/com/smart/agent/chat/ChatEvent.java`
- Create: `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Create: `src/main/java/com/smart/agent/chat/ChatController.java`
- Test: `src/test/java/com/smart/agent/chat/ChatControllerIT.java`

**Interfaces:**
- Consumes: conversation/run services, context, model gateway, tool executor, and knowledge search from Tasks 2-7.
- Produces: `POST /agent/chat/stream` with `text/event-stream` and events `message_start`, `status`, `tool_start`, `tool_result`, `citation`, `message_delta`, `message_end`, and `error`.

- [ ] **Step 1: Write the failing end-to-end SSE test**

```java
@Test
void streamsProjectToolAnswerAndPersistsAuditTrail() {
    webTestClient.post().uri("/agent/chat/stream")
        .header("X-Agent-Context", signedContextToken())
        .bodyValue(Map.of(
            "conversationId", conversationId,
            "question", "查询 project-1 项目概况",
            "pageContext", Map.of("projectId", "project-1")))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
        .returnResult(String.class)
        .getResponseBody()
        .as(StepVerifier::create)
        .expectNextMatches(s -> s.contains("message_start"))
        .thenConsumeWhile(s -> !s.contains("message_end"))
        .expectNextMatches(s -> s.contains("message_end"))
        .verifyComplete();
    assertThat(runRepository.findByConversationId(conversationId))
        .singleElement().extracting(AgentRun::status).isEqualTo(COMPLETED);
}
```

- [ ] **Step 2: Run test and verify chat API is absent**

Run: `mvn -q -Dtest=ChatControllerIT test`

Expected: FAIL with 404 or missing chat types.

- [ ] **Step 3: Implement validated chat command**

```java
public record ChatCommand(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 4000) String question,
        PageContext pageContext) {}

public record PageContext(String pageCode, String projectId, String businessType, String businessId) {}
```

Ignore tenant/user fields if supplied as unknown JSON properties by rejecting the request. Verify that page project belongs to `AgentUserContext.projectIds`.

- [ ] **Step 4: Implement orchestration order**

Exact flow:

1. Create `ai_run` in `RECEIVED`.
2. Append the user message.
3. Transition to `ROUTING` and emit `status`.
4. Transition to `PLANNING`, select relevant capability and permitted tools, and emit a non-sensitive status.
5. Search knowledge only when the question or selected capability requires it.
6. Call the model with only allowed L0/L1 tool specifications.
7. On `ToolRequested`, transition through `TOOL_SELECTING` and `TOOL_EXECUTING`, emit safe tool status, execute through `ToolExecutor`, and continue the model loop with the structured result.
8. Emit citations before answer deltas.
9. Persist only final assistant content and citations.
10. Transition `GENERATING` to `COMPLETED`, emit `message_end` with run and message IDs.
11. On error, persist a safe error code, transition to terminal state, emit `error`, and close the stream.

Limit each run to 6 model turns, 5 tool calls, 20 citations, 90 seconds total, and 64KB per tool result.

- [ ] **Step 5: Run chat integration tests**

Run: `mvn -q -Dtest=ChatControllerIT test`

Expected: PASS for normal text, project tool call, knowledge citation, missing permission, malformed tool input, model timeout, client cancellation, and trace ID propagation.

- [ ] **Step 6: Commit the vertical slice API**

```bash
git add src/main/java/com/smart/agent/chat src/test/java/com/smart/agent/chat
git commit -m "feat: stream audited agent conversations"
```

### Task 9: Add architecture rules, operational checks, and acceptance script

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/smart/agent/ArchitectureTest.java`
- Create: `src/test/java/com/smart/agent/AcceptanceIT.java`
- Create: `docs/runbooks/local-development.md`
- Create: `docs/runbooks/qdrant-rebuild.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: executable acceptance suite and operator instructions for local startup, health checking, index rebuild, and secret handling.

- [ ] **Step 1: Write failing architecture rules**

Add ArchUnit test dependency and rules:

```java
@AnalyzeClasses(packages = "com.smart.agent")
class ArchitectureTest {
    @ArchTest
    static final ArchRule toolContractsMustNotDependOnWebOrJpa = noClasses()
        .that().haveSimpleName("AgentTool")
        .or().haveSimpleName("ToolContext")
        .or().haveSimpleName("VectorIndex")
        .or().haveSimpleName("ModelGateway")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule controllersOnlyCallUseCases = classes()
        .that().resideInAPackage("..chat..")
        .and().haveSimpleNameEndingWith("Controller")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "jakarta.validation..", "org.springframework..", "..chat..", "..security..");
}
```

- [ ] **Step 2: Run architecture test and fix dependency leaks**

Run: `mvn -q -Dtest=ArchitectureTest test`

Expected before fixes: FAIL if a named domain port imports Spring Web or JPA types.

Keep the named ports framework-neutral and adapt Spring, JPA, LangChain4j, and Qdrant types inside their concrete implementation classes; do not weaken the rules to accommodate a dependency leak.

- [ ] **Step 3: Add acceptance integration test**

The test must use MySQL and Qdrant Testcontainers and the deterministic local model. It performs:

1. Apply Flyway migrations.
2. Create a tenant knowledge space.
3. Ingest text titled `项目安全检查制度`.
4. Create a conversation.
5. Submit a signed context token scoped to `project-1`.
6. Ask for project overview and applicable safety requirements.
7. Assert a project tool call occurred.
8. Assert at least one document citation is returned.
9. Assert run status is `COMPLETED`.
10. Query with `tenant-2` and assert no tenant-1 citation is returned.

- [ ] **Step 4: Write runbooks with exact commands**

`local-development.md` includes:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f compose.dev.yml up -d
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
Invoke-RestMethod http://localhost:8080/actuator/health
```

`qdrant-rebuild.md` states that MySQL metadata is authoritative, describes creating a fresh collection, streaming published chunks by tenant, validating counts, switching the configured collection alias, and deleting the old collection only after validation.

- [ ] **Step 5: Run full verification**

Run: `mvn clean verify`

Expected: all unit, architecture, MySQL, Qdrant, security, and SSE integration tests PASS.

Run: `docker compose -f compose.dev.yml config`

Expected: valid configuration with no unbound public service address and no production secret embedded.

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 6: Commit operational readiness**

```bash
git add pom.xml README.md docs src/test
git commit -m "test: verify phase one agent vertical slice"
```

## Phase 1 Vertical Slice Acceptance

The plan is complete only when all of the following are demonstrated in one automated run:

- Java 21 application starts against migrated MySQL.
- Invalid or missing engineering context is rejected.
- Tenant and project claims cannot be supplied through request JSON.
- One read-only project tool executes only for an authorized project.
- A text document is persisted in MySQL and indexed in Qdrant.
- Qdrant retrieval enforces tenant, knowledge-space, project, and published-status filters.
- One question returns both a business fact and a document citation over SSE.
- Conversation, messages, run steps, tool summary, citations, token counts, and terminal status are persisted.
- Provider failures return stable errors without exposing secrets or full sensitive content.
- No production write tool or arbitrary SQL capability exists.
- `mvn clean verify` and `git diff --check` pass.
