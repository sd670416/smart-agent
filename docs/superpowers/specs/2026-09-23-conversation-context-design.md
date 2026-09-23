# Conversation Context Design

## Goal

Keep enough structured and semantic context for follow-up questions without resending unlimited raw history.

## Layers

1. Recent messages remain verbatim within the existing message and byte limits.
2. Typed structured snapshots persist the latest business query result per conversation. The first type is `APPROVAL_QUERY`.
3. A later phase will persist a rolling summary for older messages; it is intentionally excluded from the first delivery.

## Approval Follow-ups

After a successful `approval.query`, store its normalized arguments and serialized result under the same tenant, user, and conversation. For ordinal follow-ups such as `第一条` or `第2条`, resolve the item locally and call `approval.getDetail` with its `processInstanceId`, `taskId`, and `historyId`. Detail authorization remains in smart-boot and is rechecked on every call.

Snapshots are internal context: they are not returned as chat messages and are deleted with the conversation. Only the latest snapshot of each type is retained.

## Safety And Limits

- Scope every read and write by tenant, user, and conversation.
- Cap persisted snapshot JSON and reject malformed or out-of-range ordinal references with a friendly clarification.
- Treat business values as data, not instructions.
- Do not reuse a detail payload as current truth; always call the detail tool again.

## Phase Two

When message history exceeds the configured budget, summarize older dialogue using the conversation's selected model. Persist the summary with the last summarized message sequence, retain recent messages verbatim, and inject only relevant structured snapshots.
