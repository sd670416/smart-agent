# Question records Agent implementation

Date: 2026-10-14. Current checkout retained; no branch, worktree or commit created. Existing `.gitignore` changes preserved.

## Files and behavior

- `src/main/java/com/smart/agent/question/QuestionRecordService.java`: tenant-scoped list/detail, exact run/conversation/assistant message feedback validation, independent REQUIRES_NEW snapshot collection, MySQL atomic unique-run upsert merging SYSTEM/MODEL/USER flags.
- `QuestionRecordController.java`: approved HTTP protocol, 0-based page, size 1..100, Shanghai inclusive date range, strict extra-field feedback rejection, management menu permission.
- `QuestionSnapshot.java`: bounded 2000-character content, credential/Bearer/standalone key/URL redaction; contexts limited to six messages before the question, steps limited to sixteen compact TOOL/TERMINAL/INTENT summaries. Raw MODEL and AI_DEBUG payloads are excluded.
- `UnsupportedCapabilityTool.java`: structured unsupported capability marker, compatible with the existing model provider schema converter. No extra model request and no answer keyword classification.
- `ChatOrchestrator.java`, `ChatConfiguration.java`: terminal failure collection and final model marker collection, real user/assistant message bindings. Existing required real-time business tools remain mandatory. A later successful business tool clears an earlier unsupported marker.
- `AgentRun.java`, `AgentRunService.java`, `ConversationQueryController.java`: actual message IDs persisted and exposed in run summaries; old constructors retained.
- `V15__create_question_records.sql`: verified existing highest migration was V14; adds question table, tenant/run unique key, tenant/time index and run message columns.
- Tests: `question/QuestionSnapshotTest`, `question/QuestionRecordControllerTest`, `chat/QuestionCollectionChatTest`, additional provider schema contract in `model/ModelGatewayContractTest`, persistent scenario in `PersistenceMySqlIT`.

## Evidence

- Initial `mvn -q -Dtest=QuestionSnapshotTest test` failed because the new snapshot component did not exist; then implementation passed.
- Expanded secret test first failed with a quoted multiword credential and standalone API key still visible; redaction was fixed and passes.
- Provider marker schema contract first failed with MODEL_TOOL_SCHEMA_INVALID because the provider rejects minLength/maxLength. Removed those schema keywords, retained server validation, contract now passes.
- Final serial `mvn -q test`: exit 0. Surefire XML aggregate: 359 tests, 0 failures, 0 errors, 0 skipped (including review follow-up below).
- HTTP coverage verifies no-menu denial, cross-owner feedback denial, mismatched actual assistant message denial, strict extra field rejection under lenient Jackson, inverted dates, and collection error isolation.
- Orchestrator coverage verifies failed-model SYSTEM collection with actual message binding, structured MODEL collection after completion, and normal text containing unsupported words without collection.
- `git diff --check`: exit 0; only existing Windows line-ending warnings.
- Attempted `mvn -q -Dagent.it.mysql=true -Dtest=PersistenceMySqlIT test`: Docker unavailable; Testcontainers skips it. This is NOT evidence that the live MySQL migration has run. Test includes persisted source merging/idempotency, future context exclusion, raw debug exclusion and cross-tenant detail denial.
- One intermediate full run overlapped another Maven invocation and produced missing anonymous test class errors; subsequent serial `mvn -q clean test` and final serial test succeeded. Do not run Maven concurrently in this checkout.

## Limitations and decisions

- Live MySQL migration and concurrent upsert stress testing remain to be run on an available isolated database. Production uniqueness/source merging use one database atomic upsert rather than read-then-write.
- Marker cannot skip required project/approval/board/other real-time business queries. Unknown fields that cause terminal query failure are captured as SYSTEM; a capability marker is offered after mandatory query completion. This deliberately preserves the existing trusted query boundary.
- Historical runs without actual message IDs cannot be manually reported; no heuristic backfill was introduced.
- Redaction is bounded best-effort text sanitization; no full model request or raw debug/tool result data is copied into the question record.
- Some integration and HTTP tests were added after initial implementation; the initial snapshot, expanded redaction and provider schema cases have observed red/green evidence. Do not describe the entire change as strictly test-first.

## Review follow-up: quoted multiline credentials

- Added regressions for single-quoted secrets with spaces and CR/LF, and double-quoted multiline secrets.
- Observed RED: `mvn -q -Dtest=QuestionSnapshotTest test` failed because `password='private secret value' token='first\nsecond\r\nthird'` leaked `secret value`, `second` and `third`.
- Added complete single-quoted value redaction before the general unquoted-token fallback. The negated quote character class includes CR/LF, matching the existing double-quoted behavior.
- GREEN: the five snapshot tests passed; subsequent serial full `mvn -q test` passed with exit 0. `git diff --check` passed. No application service started.
