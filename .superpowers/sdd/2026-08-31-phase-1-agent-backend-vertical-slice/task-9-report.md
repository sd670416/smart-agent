# Task 9 report - architecture and operational acceptance

## Status

DONE_WITH_CONCERNS. Architecture and default Maven verification are green. The Docker-backed Phase 1 acceptance test is compiled, discovered by Failsafe, and safely skipped without its explicit opt-in property; it was not executed because this workstation has no Docker CLI or secure Testcontainers endpoint.

## Delivered

- Added ArchUnit and the required framework-boundary rules.
- Introduced `ChatSseUseCase` so the controller depends only on its chat-package use case and Spring/security types.
- Added an opt-in `AcceptanceIT` using MySQL and Qdrant Testcontainers, Flyway, the deterministic local model, signed tenant/project/knowledge-space context, ingestion, tool execution, citations, structured run audit, token usage, terminal status, and a second-tenant isolation check.
- Configured Maven Failsafe to discover `*IT` tests during `verify`.
- Extended the deterministic local gateway only enough to complete the real project-tool loop with stable non-zero usage in acceptance testing.
- Added local-development and Qdrant-rebuild runbooks and updated the README.

## TDD and recovery evidence

- Recovered partial Task 9 work from multiple interrupted agents without reverting it.
- Focused command `mvn -q -Dtest=ArchitectureTest test` exited 0; both required ArchUnit rules passed.
- First controller-run `mvn -q clean verify` failed in Failsafe discovery with `OutputDirectoryProvider not available`, proving the explicitly pinned Failsafe `2.22.2` was incompatible with the current JUnit Platform.
- Removed only the stale Failsafe version override so Spring Boot dependency management aligns Failsafe and Surefire. The same `mvn -q clean verify` command then exited 0.

## Fresh verification

- `mvn -q -Dtest=ArchitectureTest test`: exit 0.
- `mvn -q clean verify`: exit 0.
  - Surefire: 13 classes, 86 tests, 0 failures, 0 errors, 0 skipped.
  - Failsafe: 6 classes, 28 tests, 0 failures, 0 errors, 9 skipped.
  - `AcceptanceIT` was discovered and skipped because `agent.it.acceptance=true` was not supplied. Other Docker/external integrations remained behind their existing environment gates.
- `git diff --check`: exit 0; only Git LF-to-CRLF notices were printed.
- Secret/public-bind scan found no `0.0.0.0`, unauthenticated Docker `2375`, private key, or production API-key pattern in the Task 9 artifacts. Compose ports retain the existing `127.0.0.1` default.
- `docker compose -f compose.dev.yml config` could not run because the `docker` executable is not installed on this workstation. The Compose file was inspected statically; native Compose validation remains deferred.

## Deferred environment validation

- The MySQL/Qdrant `AcceptanceIT` end-to-end path was not executed.
- Docker Compose native configuration validation was not executed.
- External Qdrant connectivity remains deferred under the existing secure-endpoint ruling.

## Concerns

- Phase 1 cannot be declared fully accepted until `mvn clean verify -Dagent.it.acceptance=true` runs against an available Docker/Testcontainers environment and the native Compose configuration command succeeds.

## Review fix round 1

- Independent review found that four SSE assertions in `AcceptanceIT` searched for backslash-escaped JSON quotes even though `WebTestClient` returns the response data with ordinary JSON quotes. The existing random-port `ChatControllerIT` response assertions provided the observed production format.
- Corrected the four event-type literals for `tool_start`, `citation`, `message_delta`, and `message_end`.
- Focused verification `mvn -q "-Dtest=ArchitectureTest,ChatControllerIT" test` exited 0.
- The Docker-backed acceptance path remains deferred, so this fix is verified against the real HTTP SSE format and compilation/default lifecycle but is not claimed as an executed container acceptance run.
