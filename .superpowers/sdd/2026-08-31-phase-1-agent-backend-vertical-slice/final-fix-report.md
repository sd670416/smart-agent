# Final review fix report

Date: 2026-09-03

## Findings closed

- Added `AGENT_LOCAL_CONTEXT_SECRET` to `.env.example` as a development-only, non-sensitive placeholder. The local runbook now requires replacing it with a random value and states that production values must come from a secret manager.
- Added an internal `__global__` project sentinel for unscoped knowledge documents. Ingestion writes the sentinel to vector payloads, search requests include only the current trusted project plus the sentinel, and authoritative MySQL metadata still accepts only a null or current-project document. External ingest/query attempts to use the reserved sentinel are rejected.
- `ModelRequest` now rejects an empty conversation before the local gateway can call `getLast()`.

## Verification

- `mvn -q -Dtest=KnowledgeIngestionServiceTest,VectorContractTest` RED before the reserved-sentinel implementation, then GREEN after it.
- `mvn -q -Dtest=PersistenceConfigurationTest,KnowledgeIngestionServiceTest,KnowledgeSearchServiceTest,VectorContractTest,ModelGatewayContractTest test`: 47 tests passed.
- `mvn -q clean verify`: exit 0. Surefire: 91 passed, 0 failed, 0 skipped. Failsafe: 28 discovered, 19 passed, 9 skipped, 0 failed. Skipped tests are `AcceptanceIT` (1), `PersistenceMySqlIT` (1), `KnowledgeMetadataMySqlIT` (1), `QdrantVectorIndexIT` (5), and `ExternalQdrantVectorIndexIT` (1), all gated or Docker-dependent.
- `git diff --check`: passed.
- Docker Compose native config check was unavailable because the `docker` executable is not installed. Static checks passed for loopback defaults on MySQL/Redis/Qdrant ports and parameterized Compose passwords; no container or database was started.

## Remaining concern

The end-to-end MySQL/Qdrant acceptance path remains unexecuted in this environment. Run `mvn clean verify -Dagent.it.acceptance=true` on a machine with a safe Docker/Testcontainers endpoint before declaring the container-backed acceptance complete.
