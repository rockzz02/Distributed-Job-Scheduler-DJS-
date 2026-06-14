# Architecture Review Notes

This document captures the main issues found during the GitHub hardening pass and the fixes applied.

## Fixed Issues

| Issue | Risk | Fix |
|---|---|---|
| Optional API documentation dependency added complexity | Extra dependency was not required for the core backend | Removed it and kept HTTP examples in README |
| Invalid schedules could be persisted | Scheduler would later skip bad jobs at runtime | Added cross-field job validation before persistence |
| Publish failure left orphan `PENDING` executions | Execution row could remain unpublished forever | Scheduler now recovers unpublished pending executions |
| Worker crash after claiming execution could leave `RUNNING` forever | Redelivered messages could be ignored because the database row was no longer pending | Scheduler now sends timed-out running executions through the retry policy |
| Retry state was partially implicit | Failed executions were hard to reason about | Added `retry_count`, `next_retry_at`, and terminal `DEAD` state |
| Error responses lacked correlation | Harder to connect API errors with logs | Added `X-Request-Id`, MDC logging, and request IDs in error responses |
| Docker verification was manual | Harder to prove local run path | Added `scripts/verify-docker.sh` |
| Integration coverage was missing | API/database/broker wiring was untested | Added Testcontainers-based API integration test |

## Current Architecture Posture

DJS is still a modular monolith. The system keeps PostgreSQL as the source of truth and uses RabbitMQ as delivery infrastructure.

Current state transitions:

```text
PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED -> PENDING
PENDING -> RUNNING -> DEAD
```

`FAILED` is intentionally retryable, not terminal. `DEAD` is terminal.

## Remaining Tradeoffs

- There is no `JobAttempt` table yet, so per-attempt history is represented by counters and timestamps only.
- Scheduler locking uses row-level pessimistic locks, which is enough for local and small multi-instance demos, but advisory lock leadership would be cleaner for larger deployments.
- Job execution is simulated. Real handlers should be added behind a handler registry.
- RabbitMQ publisher confirms are not enabled yet. The database recovery path handles unqueued pending executions, but publisher confirms would make publish acknowledgement stronger.
