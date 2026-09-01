# Task 3 report — trusted engineering user context

## Scope

Implemented immutable `AgentUserContext`, request/thread-local propagation, a local HMAC compact-token verifier, authentication filtering for `/agent/**`, stable unauthorized responses, and trace-id propagation.

## TDD evidence

- RED: the security test suite was authored before the production security types existed; the initial focused run failed during compilation because those types were missing.
- GREEN: focused verification `mvn "-Dmaven.repo.local=.m2/repository" -Dtest=AgentContextFilterTest test` passed with 20 tests.
- A regression test then exposed verifier-thrown internal `AgentException` details/status. The filter was changed to normalize all verifier failures to `AGENT_UNAUTHORIZED` / HTTP 401.
- Final full verification `mvn "-Dmaven.repo.local=.m2/repository" test` passed with 33 tests, 0 failures, 0 errors.

## Security notes

- Context claims are sourced only from the verified token; request headers and parameters cannot override them.
- HMAC signatures use constant-time comparison and expiration checks.
- Context holder and request attributes are cleared in `finally`, including async/error dispatch and downstream failures.
- Trace IDs accept only strict 32/36-character hexadecimal forms; otherwise a UUID is generated and returned.

LAN Docker-backed integration remains deferred until remote Docker coordinates are supplied.
