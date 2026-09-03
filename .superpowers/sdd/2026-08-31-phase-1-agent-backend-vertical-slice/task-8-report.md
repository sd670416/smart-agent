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

## Fix round 1 — streaming lifecycle hardening

### Recovery and RED evidence

- Recovered the interrupted 467-line working diff and the three untracked `AgentRunStep` files without reverting or rewriting it.
- `mvn -q '-Dtest=ChatControllerIT,AgentRunServiceTest' test` initially failed: the citation scenario incorrectly caused the test gateway to request a project tool without `project:read`, proving capability routing was not represented faithfully in the integration fixture.
- Replacing the unbounded sink directly with `OverflowStrategy.ERROR` produced a real `OverflowException` before Spring MVC established demand. The resulting GREEN design retains producer buffering only behind a 256-event bounded backpressure queue.
- Moving persistence behind the run deadline exposed Reactor's prohibition on `block()` from the parallel scheduler. Publishing model callbacks on `boundedElastic` made the absolute-deadline persistence boundary safe.
- A compile RED caught the exact `ModelRequest.allowedToolSpecifications()` contract and another compile RED caught a non-effectively-final audit variable; both were corrected before rerunning tests.

### Review findings resolved

- **C1 cancellation linearization:** completion, failure, and cancellation now serialize on one terminal lock. Cancellation disposes the current model subscription, wins only from a non-terminal state, and prevents subsequent model events, tool result audit, assistant message, or terminal success emission.
- **C2 absolute 90-second deadline:** run creation, user append, status transitions, knowledge retrieval/audit, tool execution/audit, model streaming, and atomic completion all consume the same absolute budget. Any budget expiry maps to `AGENT_RUN_TIMEOUT` / `TIMEOUT`; terminal persistence remains allowed so the timeout itself is durable.
- **C3 structured audit:** `ai_run_step` now stores scoped MODEL, KNOWLEDGE_SEARCH, TOOL, and TERMINAL steps. Tool steps include safe key, risk, outcome, duration, and result size; model steps include every completed turn's token usage, including tool-request turns. Raw prompts, evidence, tool results, and provider error details are excluded.
- **I1 incremental SSE:** every `TextDelta` is forwarded as it arrives; model events are no longer collected before emission. Active subscriptions are cancellable.
- **I2 multiple tools:** all `ToolRequested` events in a turn execute in arrival order under the global five-call cap, after recording that turn's `Completed` usage.
- **I3 atomic terminal persistence:** assistant append, final usage, MODEL audit step, and COMPLETED transition share one transaction; failure/cancellation use transactional terminal methods. `message_end` is emitted only after the transaction succeeds. A persistence-failure integration test proves no false success or assistant message is emitted.
- **I4 untrusted tool provenance:** tool results enter the next request with an explicit `UNTRUSTED_TOOL_RESULT` provenance label and instruction boundary, reinforced by the trusted system instruction catalog.
- **I5 capability routing:** project tools are supplied only for project/current-page questions and only after server-side permission filtering; signed page context is injected as trusted context. Knowledge routing remains keyword/capability gated and project/space scoped by the server context.
- **I6 scoped reads:** the unscoped/default-empty `findByConversationId` contract was removed. Production and tests use tenant + user + conversation scope.
- **I7 production-semantics tests:** coverage now proves first delta precedes completion, multi-tool ordering and accumulated non-zero tokens, cancellation before and during model generation, absence of post-cancel persistence, absolute knowledge deadline, structured safe audit, and completion-persistence failure behavior.
- **Minor:** provider failures now map to stable `AGENT_MODEL_TIMEOUT` or `AGENT_MODEL_FAILED`; orchestration, tool, and persistence failures remain distinct. SSE backpressure is capped at 256 queued events and answer content remains capped at 64 KiB.

### Database compatibility

- Existing committed V3 was left unchanged to preserve Flyway checksums. New V4 adds nullable `ai_run_step.user_id`, backfills it from `ai_run`, then enforces `NOT NULL` and creates the scoped index.

### GREEN verification

- `mvn -q '-Dtest=ChatControllerIT,AgentRunServiceTest' test`: pass (19 tests after the final fix set).
- `mvn -q test`: pass (96 default Docker-independent tests, 0 failures/errors/skips).
- `git diff --check`: pass; only Git's existing LF-to-CRLF notices were printed.

## Fix round 2 — close chat lifecycle races

### RED evidence

- `mvn -q '-Dtest=ModelGatewayContractTest,ChatControllerIT' test` first failed to compile because the required typed `ModelRequest.ToolResultMessage` did not exist.
- After introducing the wished-for typed test API, the same focused command produced four behavioral failures: cancellation at `tool_start` still wrote `project.getOverview:INVALID_INPUT`; successful runs lacked `TERMINAL`; callback audit deadline expiry timed out the test while leaving the stream open; and a 65,537-byte Completed-only answer was emitted as `message_delta`.
- The failing callback test also logged an `onErrorDropped` timeout from `finishModelTurn`, directly reproducing the completion-callback escape path.

### GREEN changes

- **C1:** tool execution permission is now acquired under the same terminal lock as cancellation. A cancellable future is installed before releasing the lock. Cancellation before permission prevents invocation; cancellation after start cancels/interrupts the future and all result audit, follow-up model work, and assistant persistence remain suppressed.
- **C2:** model item and completion callbacks now have guarded entry points that map every runtime exception. Any nested absolute-budget timeout reaches `fail(AGENT_RUN_TIMEOUT, TIMEOUT)` and persists the terminal state.
- **C3:** atomic successful completion now appends a safe `TERMINAL/COMPLETED` step immediately after the final MODEL step in the same transaction.
- **I4:** model history now uses the sealed `ConversationEntry` contract. Tool output is a typed `ToolResultMessage(callId, toolKey, content)`, never a user/system message. The OpenAI-compatible gateway maps it to LangChain4j `ToolExecutionResultMessage` with an explicit untrusted provenance boundary; the local gateway recognizes the type without echoing its content.
- **I7:** deterministic tests cover cancellation immediately at tool start, interruption after actual tool invocation, absence of tool audit/assistant messages, callback-audit deadline expiry, successful terminal-step ordering, typed adapter role mapping, and oversized Completed-only output.
- **Minor2:** final answer selection now performs a UTF-8 64 KiB check before fallback delta emission or atomic persistence, closing the Completed-only bypass.

### Verification

- `mvn -q '-Dtest=ModelGatewayContractTest,ChatControllerIT,AgentRunServiceTest' test`: pass (41 focused tests).
- `mvn -q test`: pass (101 default Docker-independent tests, 0 failures/errors/skips).
- `git diff --check`: pass; only existing LF-to-CRLF notices were printed.

## Fix round 3 — linearize tool audit cancellation

### RED

- Added a deterministic serialization gate that pauses after the tool has returned but before tool audit begins. A spy on the scoped run audit service observes whether the TOOL summary is attempted.
- `mvn -q '-Dtest=ChatControllerIT#cancellationAfterToolReturnsButBeforeAuditSuppressesEveryLaterSideEffect' test` failed because `toolAuditAttempted` became true after cancellation, reproducing the post-return race without relying on timing sleeps.

### GREEN

- Result serialization remains outside the terminal lock, so no long-running external tool call is performed while holding it.
- After serialization, the final cancellation check, safe tool summary persistence, structured TOOL step, typed assistant/tool-result history append, and `tool_result` event now form one terminal-lock commit section.
- If cancellation wins before that section, every later side effect is skipped. If the tool commit section wins, cancellation waits and observes the already-linearized commit.
- Tool audit persistence failures have a dedicated wrapper and map to `AGENT_PERSISTENCE_FAILED` or `AGENT_RUN_TIMEOUT`; they no longer fall through as invalid tool input, and synchronized cleanup remains reentrant/deadlock-free.
- The deterministic GREEN test asserts: no tool summary, only MODEL then cancellation TERMINAL steps, exactly one model call, no assistant message, and terminal `CANCELLED`.

### Verification

- `mvn -q '-Dtest=ChatControllerIT,AgentRunServiceTest' test`: pass (24 focused tests).
- `mvn -q test`: pass (102 default Docker-independent tests, 0 failures/errors/skips).
- `git diff --check`: pass; only existing LF-to-CRLF notices were printed.
