# SDD ledger — plan: docs/superpowers/plans/2026-08-31-phase-1-agent-backend-vertical-slice.md

Spec: docs/superpowers/specs/2026-08-31-smart-agent-system-design.md
Branch: feature/phase1-agent-backend
Worktree: F:/project/gongcheng/agent/.worktrees/phase1-agent-backend

## Execution rulings

- Ruling: Docker services run on a LAN Docker host, not a local daemon — all host addresses remain externally configurable; Docker/Testcontainers verification runs only when `DOCKER_HOST` is supplied — cost if wrong: integration tests wait for remote host configuration.
- Ruling: `compose.dev.yml` is retained as deployment input but no local `docker compose up` command will be executed — the same file may be targeted at the remote Docker context after host details are available — cost if wrong: local smoke verification is deferred.
- Ruling: Maven dependency downloads may require network approval; request it only if sandboxed resolution fails — cost if wrong: Task 1 verification pauses at dependency resolution.

## Pre-flight consistency scan

| Scope | Produces / consumes | Finding |
|---|---|---|
| Task 1 | Produces build, runtime config and infrastructure variables used by Tasks 2-9 | Consistent; remote-Docker ruling replaces local daemon execution only. |
| Task 2 | Produces conversation/run services consumed by Tasks 4 and 8 | Consistent; includes PLANNING transition required by spec. |
| Task 3 | Produces trusted AgentUserContext consumed by Tasks 4, 7 and 8 | Consistent; request JSON is never trusted for claims. |
| Task 4 | Produces AgentTool, ToolExecutor and project.getOverview consumed by Tasks 5 and 8 | Consistent; phase-one tool is L1 and read-only. |
| Task 5 | Produces ModelGateway event stream consumed by Task 8 | Consistent; local deterministic adapter makes tests model-independent. |
| Task 6 | Produces knowledge metadata, ingestion and VectorIndex consumed by Task 7 | Consistent; MySQL remains authoritative and Qdrant rebuildable. |
| Task 7 | Produces KnowledgeSearchService and citations consumed by Task 8 | Consistent; trusted tenant/space/project filters are mandatory. |
| Task 8 | Consumes Tasks 2-7 and exposes the complete SSE vertical slice | Consistent; writes only chat/audit metadata, not business data. |
| Task 9 | Consumes all prior deliverables for architecture and acceptance verification | Consistent; Docker-backed acceptance depends on configured LAN Docker host. |
| Tasks 1 + 9 | Both modify pom.xml and README.md | Intentional: Task 1 bootstraps; Task 9 adds verification dependencies and operator docs. |
| Tasks 6 + 7 | Share knowledge package contracts | Consistent names: VectorIndex, VectorSearchQuery, VectorHit, KnowledgeCitation. |
| Tasks 4 + 8 | Share ToolExecutor and project.getOverview | Consistent signature and tool key. |
| Tasks 5 + 8 | Share ModelGateway.stream(ModelRequest) | Consistent Flux<ModelEvent> contract. |

## Task status

- Task 1: fix round 1/5 started — reviewer found 1 critical, 2 important, 1 minor issue. Required ruling: Compose binds default to loopback but supports `AGENT_DOCKER_BIND_HOST`; `.env` workflow must show how to inject variables; insecure unauthenticated `tcp://host:2375` example must be removed in favor of a named Docker context or TLS endpoint.
- PAUSED BY USER (2026-08-31): Task 1 fix round 1 implementation is present but uncommitted in `.env.example`, `README.md`, `compose.dev.yml`, and `src/main/resources/application-local.yml`. Implementer reported configuration RED/GREEN and focused test PASS, but full test/package/self-review/report append/commit were interrupted. Resume the same implementer or a recovery implementer, verify the four-file diff, finish tests, append report, commit, generate a scoped review package from `b25910d`, and dispatch re-review before marking Task 1 complete.
- Task 1: fix round 1/5 complete — 4 findings addressed, 0 open; commit `6cc92cb`; scoped re-review clean.
- Task 1: complete — commits `b25910d..6cc92cb`; focused/full tests, package, YAML structure, environment import, security scan and JAR inspection passed. Docker-native validation remains deferred until LAN Docker access is configured.
- Task 2: fix round 1/5 started — reviewer found 1 critical tenant-isolation defect, 2 important persistence/configuration-test gaps, and 1 minor validation gap.
- Ruling: Task 2 service/repository signatures may add trusted `tenantId` and `userId` parameters beyond the original plan signatures — global tenant-isolation constraints override the unsafe convenience signatures — cost if wrong: later Task 8 call sites must use `AgentUserContext` claims explicitly.
- Ruling: production persistence uses an explicit `agent.persistence.enabled=true` default rather than component-scan `@ConditionalOnBean` ordering; no-database context tests set it false — cost if wrong: operators must not disable persistence outside isolated tests.
- Ruling: MySQL/Testcontainers integration tests may be authored and tagged for LAN-Docker execution but cannot be claimed green until Docker coordinates are supplied — cost if wrong: JPA/Flyway runtime verification remains deferred, visibly, instead of being simulated with H2.
- Task 2: fix round 1/5 complete — all 4 findings addressed, 0 open; commit `a428069`; scoped re-review clean.
- Task 2: complete — commits `a99bbf6..a428069`; 13 non-Docker tests passed. Opt-in `PersistenceMySqlIT` is present but deferred until LAN Docker coordinates are configured.
- Task 3: complete — trusted engineering user context, HMAC local verifier, authentication/trace filters, stable 401 errors, and cleanup guarantees implemented; focused security tests 20/20 and full non-Docker suite 33/33 passed. LAN Docker-backed integration remains deferred.
- Task 4: Ruling: the required `ToolExecutor.execute(String, Object, AgentUserContext)` contract carries no `runId`, so Task 4 records only a safe structured execution summary through `AgentRunService` without inventing a run association; Task 8 will persist the summary against its real run ID — this preserves the published Task 4 interface, but persistent per-run tool audit is deferred until orchestration exists.
- Task 4: fix round 1/5 complete — 1 Important finding addressed, 0 open; commits `e75b5bb..d8b2a04`; scoped re-review clean.
- Task 4: complete — commits `197393b..d8b2a04`; focused tests 12/12 and full non-Docker suite 43/43 passed; MySQL integration test remains opt-in/skipped; review clean.
- Task 5: minor (deferred): `ModelRequest` permits an empty conversation while `LocalDeterministicModelGateway` immediately reads the last message; final review should decide whether to reject it at construction or return a stable failure event.
- Task 5: fix round 1/5 complete — 2 Critical and 3 Important findings addressed, 0 open; commits `1d5e5db..27a1dc5`; scoped re-review clean.
- Task 5: complete — commits `d025320..27a1dc5`; model gateway tests 17/17 and full non-Docker suite 61/61 passed; one existing MySQL/Testcontainers test skipped; review clean with one deferred minor.
- Task 6: integration deferred — Qdrant and MySQL Testcontainers require a secure Docker endpoint; the external Qdrant attempt was blocked by the sandbox at `192.168.0.179:6334` with `Permission denied: getsockopt` and is not claimed as passed. A previously interrupted elevated attempt has no completed result and may have left only a uniquely prefixed test collection.
- Task 6: complete — commit `82249b2`; independent task review found no Critical, Important, or Minor issues. Controller verification `mvn clean test` passed 73/73 tests across 11 classes with 0 skipped, and `git diff --check` passed; Docker-backed and external Qdrant integration remain explicitly deferred.
