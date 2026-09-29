# Approval Semantic Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep supported approval filters unchanged and provide a bounded, permission-preserving model filter for unsupported semantic conditions before pagination.

**Architecture:** `smart-boot` remains the authority for visibility and candidate records. `agent` requests every candidate page, asks the bound model to classify batches using only returned evidence, validates IDs, then counts and pages the matches. Unknown or incomplete results fail closed.

**Tech Stack:** Java 21, Spring Boot, Reactor, Jackson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-29-approval-semantic-fallback-design.md`

## Global Constraints

- Work only in `smart-boot/dev2`, `smart-web/dev2`, and `agent/dev`.
- Preserve all existing uncommitted changes.
- Do not run compilation, build, or tests; the user will do so.
- The original approval API performs permission filtering before any model receives candidate data.
- Never report a complete total from a partial candidate or model batch.

---

### Task 1: Candidate Contract

**Files:** `smart-boot/smart-ai/src/main/java/com/smart/ai/approval/AiApprovalQueryService.java`, approval query request/response types and focused tests.

**Interfaces:** Existing signed `/internal/ai/tools/approval-query` remains the entry point. A candidate mode returns unfiltered, permission-scoped pages with stable record identifiers and total.

- [ ] Write focused contract tests for SELF and ALL visibility, complete paging, and candidate limit.
- [ ] Add candidate mode to the existing approval query contract without bypassing visibility resolution.
- [ ] Ensure list fields provide judgment evidence while excluding unrelated sensitive data.

### Task 2: Agent Fallback

**Files:** `agent/src/main/java/com/smart/agent/chat/ChatOrchestrator.java`, a focused semantic-filter service, and approval client/input types.

**Interfaces:** On unsupported `approval.query` filter only, obtain all candidate pages via the signed client. Return a normal `ApprovalQueryResult` after verified model selection, total calculation and pagination.

- [ ] Write tests for supported-query pass-through, unsupported-query fallback, forged IDs, unknown decisions, incomplete pages and model failure.
- [ ] Fetch all candidate pages up to 10000, with total and record identity verification.
- [ ] Classify fixed-size batches using the current run's model binding and strict structured output.
- [ ] Reject outputs containing IDs outside their batch and fail closed when any batch is incomplete.
- [ ] Sort, count, paginate, audit and pass the final result to the normal response path.

### Task 3: User-Facing Validation

**Files:** agent chat tests, optional `smart-web` rendering only if existing result format is insufficient.

- [ ] Add regression cases for `3 天没处理的待办` and an unsupported semantic condition.
- [ ] Ensure insufficient evidence and over-limit cases display explicit non-success messages.
- [ ] Inspect the final diff and give the user restart and manual verification steps. Do not run build or tests.
