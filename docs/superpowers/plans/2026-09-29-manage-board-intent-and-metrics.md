# Manage Board Intent and Metrics Implementation Plan

**Goal:** Route ambiguous project/board requests through clarification and expose every complete-version manage board display module to AI queries.

**Architecture:** Keep domain routing in agent and board permissions/data access in smart-boot. Reuse the existing company board service outputs and present scalar, grouped-series, and paged-table results with explicit result shapes. Compare only compatible shapes.

**Tech Stack:** Java 21 Spring Boot agent; Java 8 Spring Boot smart-boot; JUnit; Vue page contracts.

**Spec:** User-approved design in the current conversation (2026-09-29).

## Global Constraints

- Do not run compilation, builds, or tests; the user performs those steps.
- Preserve existing unrelated changes and menu authorization.
- Query current data for each turn; never answer from historical result rows alone.

### Task 1: Route and Clarification

**Files:** `agent/src/main/java/com/smart/agent/routing/QueryIntentResolver.java`, `agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java`, related routing tests.

- [x] Add regression cases for unambiguous board metrics, explicit project/archive queries, and ambiguous project queries.
- [x] Make the resolver result authoritative when it is resolved or needs clarification; preserve the selected-domain path.
- [x] Ensure UNKNOWN does not force a project or board route, and ambiguous requests enter the existing persisted clarification flow.
- [x] Check the confirmation carries the original question into a new live tool call.

### Task 2: All Manage Metrics

**Files:** `smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiBoardToolController.java`, `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/board/BoardQueryDefinitionRegistry.java`, `agent/src/main/java/com/smart/agent/tool/board/BoardQueryTool.java`.

- [x] Add the three pie distributions, five bar analyses, and two paged tables to the manage metric catalog.
- [x] Adapt the existing pie/bar service maps to typed scalar/grouped results without changing their calculation scope.
- [x] Adapt paged table rows and preserve pagination, filters and menu authorization.
- [x] Keep existing six scalar metrics and region distribution backward compatible.

### Task 3: Compare and Review

**Files:** `smart-boot/smart-ai/src/main/java/com/smart/ai/controller/AiBoardToolController.java`, relevant tests.

- [x] Compare scalar values numerically and grouped results by aligned dimension key.
- [x] Reject incomparable tabular/shape combinations with a clear message rather than fabricating a scalar difference.
- [x] Perform static diff and contract review. User runs builds and tests.

Code is written but compilation, automated tests, and end-to-end verification remain with the user by request.
