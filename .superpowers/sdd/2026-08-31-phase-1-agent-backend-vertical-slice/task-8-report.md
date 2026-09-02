# Task 8 Report — Audited SSE chat orchestration

## Recovery audit

- Resumed from baseline `c5d8f28909cb784e5003250736f03d8da83f5f48` after two user-requested pauses.
- Preserved and reviewed all six untracked `chat` files plus the existing run/tool modifications before editing.
- The recovered slice compiled and its single original happy-path integration test passed, but it did not yet cover strict request claims, citations, safe audit persistence, failure paths, cancellation, or limits.

## Delivered behavior

- `POST /agent/chat/stream` emits ordered `message_start`, safe status, tool, citation, delta, terminal, and error SSE events.
- Conversation/project scope comes only from signed `AgentUserContext`; unknown request fields (including identity or permission claims) are rejected.
- The model receives only permission-filtered L0/L1 tool schemas. Tool results are supplied as escaped, untrusted conversation data rather than system instructions.
- Knowledge retrieval is demand-gated and uses only token-authorized knowledge spaces and project scope. Citations are capped at 20 and emitted before answer deltas.
- Runs enforce 6 model turns, 5 tool calls, 20 citations, 90 seconds, and the existing `ToolExecutor` 64 KiB result limit.
- Run status, trace ID, token counts, safe tool outcome summaries, citation tokens, safe error codes, cancellation, and terminal states are persisted. Only the completed assistant response is appended; partial deltas and raw tool results are not persisted.
- Flyway V3 adds the run audit columns without placing secrets or raw evidence in audit fields.

## TDD evidence

- Recovery baseline: original `ChatControllerIT` happy path passed.
- RED: untrusted `tenantId`/`permissions` claims returned 200 instead of 400; permission-denied run remained `RECEIVED`.
- GREEN: strict Jackson deserialization and the missing state transition made both behaviors pass.
- RED: knowledge test failed to compile before the knowledge-aware orchestrator API existed.
- GREEN: trusted retrieval, evidence transfer, citation-before-delta ordering, and citation audit persistence passed.
- RED: direct reactive cancellation left the run in `GENERATING`.
- GREEN: sink disposal cancellation persisted `CANCELLED` and disposed the model subscription.
- Added passing coverage for normal answer, project tool, citation, malformed tool input, stable timeout, trace ID, unknown claims, ordering, five-tool limit, and twenty-citation cap.

## Verification

- `mvn -q -Dtest=ChatControllerIT test`: pass (9 tests).
- `mvn -q test`: pass (91 default Docker-independent tests, 0 failures/errors).
- `git diff --check`: pass (only Git line-ending notices).

## Deferred environment checks

- Docker/Testcontainers and external Qdrant checks remain opt-in/environment-dependent and were not required by this task's default suite.
