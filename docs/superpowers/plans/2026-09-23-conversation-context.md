# Conversation Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist approval query context so ordinal follow-ups reliably open the selected approval detail, then add bounded rolling summaries in a separate phase.

**Architecture:** A generic conversation-context store keeps one payload per context type and security scope. The chat orchestrator saves successful approval queries and resolves ordinal follow-ups before the model call.

**Tech Stack:** Java 21, Spring Boot, JPA, Flyway, Jackson, JUnit 5

**Spec:** `docs/superpowers/specs/2026-09-23-conversation-context-design.md`

## Global Constraints

- Work in the current checkout; do not create a worktree.
- Do not run builds or tests; the user performs verification.
- Never bypass smart-boot detail authorization.
- Keep snapshot payloads internal and tenant/user/conversation scoped.

---

### Task 1: Approval query snapshot persistence

**Files:**
- Create: `src/main/resources/db/migration/V13__create_conversation_context.sql`
- Create: `src/main/java/com/smart/agent/context/ConversationContext.java`
- Create: `src/main/java/com/smart/agent/context/ConversationContextRepository.java`
- Create: `src/main/java/com/smart/agent/context/JpaConversationContextRepository.java`
- Create: `src/main/java/com/smart/agent/context/ConversationContextService.java`
- Test: `src/test/java/com/smart/agent/context/ConversationContextServiceTest.java`

- [x] Write repository/service tests for scoped upsert and latest lookup.
- [x] Add the migration, entity, repository, and service.
- [x] Ensure conversation deletion removes stored contexts.
- [x] Perform static diff validation. (2026-09-23; build/tests intentionally left to user)

### Task 2: Ordinal approval follow-up resolution

**Files:**
- Modify: `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Modify: `src/main/java/com/smart/agent/chat/ChatConfiguration.java`
- Modify: `src/test/java/com/smart/agent/chat/ChatControllerIT.java`

- [x] Add an integration test: query approval list, ask `第一条`, and verify `approval.getDetail` receives the first item's IDs.
- [x] Persist successful `approval.query` results through `ConversationContextService`.
- [x] Resolve Chinese and Arabic ordinals against the latest snapshot and execute the detail tool before the model response.
- [x] Return a clarification for missing or out-of-range snapshots.
- [x] Perform static diff validation and hand off for user testing. (2026-09-23; awaiting user build/test)

### Task 3: Rolling summary (second delivery)

**Files:**
- Create summary model/service files under `src/main/java/com/smart/agent/context/`.
- Modify: `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`
- Add focused tests under `src/test/java/com/smart/agent/context/`.

- [ ] Add tests for threshold-based summarization and recent-message retention.
- [ ] Persist summary and summarized-through sequence in `ai_conversation_context`.
- [ ] Inject summary, recent messages, and only relevant structured snapshots within one context budget.
- [ ] Perform static diff validation and hand off for user testing.
