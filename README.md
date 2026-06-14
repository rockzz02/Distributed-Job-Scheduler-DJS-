# Distributed Job Scheduler (DJS)

DJS is a production-style backend project that schedules jobs, persists execution state, publishes runnable work to RabbitMQ, executes jobs asynchronously with workers, retries failures, and marks exhausted executions as dead.

The project is intentionally scoped for a Backend / SDE-2 portfolio: one Spring Boot application, one PostgreSQL database, one RabbitMQ broker, Docker Compose for local development, and no frontend, Kubernetes, Kafka, Redis, or microservices.

## What It Demonstrates

- REST API design with validation and error handling.
- Durable scheduling state in PostgreSQL.
- Queue-based asynchronous execution with RabbitMQ.
- Worker-side idempotency and guarded state transitions.
- Retry handling with fixed and exponential backoff.
- Local production-like runtime using Docker Compose.
- Unit and integration testing with JUnit, Mockito, and Testcontainers.
- Clean modular-monolith package boundaries.

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Persistence | Spring Data JPA, PostgreSQL, Flyway |
| Messaging | RabbitMQ, Spring AMQP |
| Testing | JUnit 5, Mockito, Testcontainers |
| Runtime | Docker Compose |
| Build | Maven |

## Architecture

```text
User
  |
  v
REST API
  |
  v
PostgreSQL
  |
  v
Scheduler
  |
  v
RabbitMQ
  |
  v
Worker
  |
  v
PostgreSQL
```

PostgreSQL is the source of truth. RabbitMQ is a delivery mechanism. If a message is duplicated or a publish fails, the database state machine is what keeps the system recoverable.

## Components

| Component | Package | Responsibility |
|---|---|---|
| API | `com.djs.api` | Job CRUD endpoints, DTOs, mapping |
| Job domain | `com.djs.job` | Job persistence, validation, job service |
| Execution | `com.djs.execution` | Execution entity, status, repository queries |
| Scheduler | `com.djs.scheduler` | Finds due jobs and due retries every 10 seconds |
| Queue | `com.djs.queue` | RabbitMQ topology, publisher, consumer, message DTO |
| Worker | `com.djs.worker` | Claims executions, simulates work, updates status |
| Retry | `com.djs.retry` | Retry decisions, retry count, dead-state handling |
| Common | `com.djs.common` | Error responses, request logging, MDC correlation |
| Config | `com.djs.config` | Clock, JPA auditing, transaction configuration |

## Features

### Job Management

- Create jobs with one-time, fixed-rate, or cron schedules.
- List jobs with pagination.
- Fetch a job by ID.
- Soft-delete jobs.
- Reject invalid schedule and retry combinations before persistence.

### Scheduling

- Spring Scheduler runs every 10 seconds by default.
- Finds active jobs whose `next_run_at` is due.
- Creates `job_executions` records.
- Publishes execution messages to RabbitMQ.
- Recovers unpublished `PENDING` executions.
- Republishes failed executions whose `next_retry_at` is due.
- Sends timed-out `RUNNING` executions back through retry handling.

### Worker Execution

- RabbitMQ consumer receives `JobExecutionMessage`.
- Worker claims only `PENDING` executions.
- Execution becomes `RUNNING`.
- `timeoutSeconds` controls how long the worker claim can remain active.
- Work is simulated with a short delay and random success/failure.
- Success marks execution `SUCCESS`.
- Failure delegates to retry handling.

### Retry Support

- Each job has `maxRetries`, `retryStrategy`, and `retryDelaySeconds`.
- Retry count is stored in `job_executions.retry_count`.
- `FIXED` uses the same delay for each retry.
- `EXPONENTIAL` grows delay by retry attempt.
- `NONE` marks the first failure as `DEAD`.
- When retries are exhausted, execution becomes `DEAD`.

### Reliability

- PostgreSQL stores job definitions, execution status, retry count, retry timing, and errors.
- RabbitMQ messages carry IDs, not full job payloads.
- Workers ignore duplicate messages for non-`PENDING` executions.
- Scheduler recovers worker crashes by checking execution lock expiry.
- Request logs include `X-Request-Id`.
- Queue logs include message trace IDs.

## Execution State Machine

```text
PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED -> PENDING
PENDING -> RUNNING -> DEAD
```

| State | Meaning |
|---|---|
| `PENDING` | Execution is ready to publish or consume |
| `RUNNING` | Worker claimed the execution |
| `SUCCESS` | Execution completed successfully |
| `FAILED` | Execution failed and is waiting for retry |
| `DEAD` | Retries are exhausted; terminal failure |

## API

Base URL when running locally:

```text
http://localhost:8080
```

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/jobs` | Create a job |
| `GET` | `/jobs` | List non-deleted jobs |
| `GET` | `/jobs/{id}` | Get a job by ID |
| `DELETE` | `/jobs/{id}` | Soft-delete a job |
| `GET` | `/actuator/health` | Health check |

## Example Requests

Use a future `nextRunAt`. Most examples below use year `2099` so they remain valid.

### Create a Runnable Demo Job

This one-time job is scheduled two minutes in the future so the scheduler and worker actually pick it up during a demo. The job itself is still a simulated `NOOP`; the visible terminal output is the job status changing from `ACTIVE` to `COMPLETED`.

```bash
NEXT_RUN_AT=$(python3 -c 'from datetime import datetime, timezone, timedelta; print((datetime.now(timezone.utc) + timedelta(minutes=2)).isoformat().replace("+00:00", "Z"))')
RESPONSE=$(curl -s -X POST http://localhost:8080/jobs \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: demo-runnable-1' \
  -d '{
    "name": "demo-runnable-job",
    "description": "Runs shortly after creation so the scheduler and worker activity is visible",
    "type": "NOOP",
    "payload": {
      "source": "curl-demo"
    },
    "scheduleType": "ONE_TIME",
    "cronExpression": null,
    "intervalSeconds": null,
    "nextRunAt": "'"$NEXT_RUN_AT"'",
    "maxRetries": 3,
    "retryStrategy": "FIXED",
    "retryDelaySeconds": 10,
    "timeoutSeconds": 120
  }')

JOB_ID=$(printf '%s' "$RESPONSE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
echo "created job: $JOB_ID"
echo "scheduled for: $NEXT_RUN_AT"

for attempt in $(seq 1 60); do
  JOB_JSON=$(curl -s "http://localhost:8080/jobs/$JOB_ID")
  STATUS=$(printf '%s' "$JOB_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
  printf '%s status=%s\n' "$(date -u +%H:%M:%S)" "$STATUS"

  if [ "$STATUS" = "COMPLETED" ]; then
    break
  fi

  sleep 5
done
```

Expected output ends like this:

```text
created job: 42cf2fc3-3f51-43d4-b342-fb4ad5fd704a
scheduled for: 2026-06-14T12:02:00Z
12:00:04 status=ACTIVE
12:00:09 status=ACTIVE
...
12:02:14 status=COMPLETED
```

### Create a Fixed-Rate Job

```bash
curl -X POST http://localhost:8080/jobs \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: demo-fixed-rate-1' \
  -d '{
    "name": "demo-fixed-rate-job",
    "description": "Runs a simulated job every minute",
    "type": "NOOP",
    "payload": {
      "source": "curl"
    },
    "scheduleType": "FIXED_RATE",
    "cronExpression": null,
    "intervalSeconds": 60,
    "nextRunAt": "2099-01-01T00:00:00Z",
    "maxRetries": 3,
    "retryStrategy": "EXPONENTIAL",
    "retryDelaySeconds": 30,
    "timeoutSeconds": 120
  }'
```

### Create a One-Time Job

```bash
NEXT_RUN_AT=$(python3 -c 'from datetime import datetime, timezone, timedelta; print((datetime.now(timezone.utc) + timedelta(minutes=2)).isoformat().replace("+00:00", "Z"))')

curl -i -X POST http://localhost:8080/jobs \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: demo-runnable-1' \
  -d '{
    "name": "demo-runnable-job",
    "description": "Runs shortly after creation so the scheduler and worker activity is visible",
    "type": "NOOP",
    "payload": {
      "source": "curl-demo"
    },
    "scheduleType": "ONE_TIME",
    "cronExpression": null,
    "intervalSeconds": null,
    "nextRunAt": "'"$NEXT_RUN_AT"'",
    "maxRetries": 3,
    "retryStrategy": "FIXED",
    "retryDelaySeconds": 10,
    "timeoutSeconds": 120
  }'
```

### Create a Cron Job

Spring cron expressions use six fields.

```bash
curl -X POST http://localhost:8080/jobs \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "demo-cron-job",
    "description": "Runs every five minutes",
    "type": "NOOP",
    "payload": {},
    "scheduleType": "CRON",
    "cronExpression": "0 */5 * * * *",
    "intervalSeconds": null,
    "nextRunAt": "2099-01-01T00:00:00Z",
    "maxRetries": 3,
    "retryStrategy": "FIXED",
    "retryDelaySeconds": 30,
    "timeoutSeconds": 120
  }'
```

### List Jobs

```bash
curl 'http://localhost:8080/jobs?page=0&size=20'
```

### Get a Job

```bash
curl http://localhost:8080/jobs/{jobId}
```

### Delete a Job

```bash
curl -X DELETE http://localhost:8080/jobs/{jobId}
```

## Run Locally

### Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop or another Docker Compose-compatible runtime

### Start Everything

```bash
docker compose up --build
```

Services:

| Service | URL / Port |
|---|---|
| DJS API | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |
| RabbitMQ AMQP | `localhost:5672` |
| RabbitMQ UI | `http://localhost:15672` |

RabbitMQ credentials:

```text
username: djs
password: djs
```

### Verify Docker Startup

```bash
./scripts/verify-docker.sh
```

The script validates Compose config, starts the stack, waits for `/actuator/health`, and prints service status.

### Stop

```bash
docker compose down
```

### Reset Local Data

```bash
docker compose down -v
```

## Run Tests

Unit and integration tests:

```bash
mvn test
```

The integration test uses Testcontainers for PostgreSQL and RabbitMQ.

## Configuration

Important environment variables:

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | API port |
| `DJS_DATABASE_URL` | `jdbc:postgresql://localhost:5432/djs` | JDBC URL |
| `DJS_DATABASE_USERNAME` | `djs` | Database username |
| `DJS_DATABASE_PASSWORD` | `djs` | Database password |
| `DJS_RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `DJS_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `DJS_RABBITMQ_USERNAME` | `djs` | RabbitMQ username |
| `DJS_RABBITMQ_PASSWORD` | `djs` | RabbitMQ password |
| `DJS_SCHEDULER_ENABLED` | `true` | Enables scheduler loop |
| `DJS_SCHEDULER_POLL_INTERVAL` | `10s` | Scheduler delay |
| `DJS_SCHEDULER_BATCH_SIZE` | `100` | Jobs/retries processed per tick |
| `DJS_WORKER_ENABLED` | `true` | Enables RabbitMQ consumer |
| `DJS_WORKER_CONCURRENCY` | `4` | Consumer concurrency |
| `DJS_LOG_LEVEL` | `INFO` | Application log level |

## Database Migrations

| Migration | Purpose |
|---|---|
| `V1__create_jobs_and_job_executions.sql` | Creates `jobs` and `job_executions` |
| `V2__constrain_job_execution_statuses.sql` | Normalizes old status names and constrains execution status |
| `V3__add_retry_count_and_dead_status.sql` | Adds retry count and terminal `DEAD` status |
| `V4__add_job_domain_constraints.sql` | Adds job status, schedule, and retry consistency checks |
| `V5__add_execution_state_constraints.sql` | Adds execution state consistency checks |

## Project Structure

```text
src/main/java/com/djs
  api
  common
  config
  execution
  job
  queue
  retry
  scheduler
  worker
```

## Design Decisions

- Modular monolith instead of microservices for local simplicity.
- PostgreSQL owns durable state.
- RabbitMQ is used only for delivery.
- Failed executions are retried by scheduler scanning, not RabbitMQ requeue loops.
- Duplicate messages are tolerated by checking execution status before worker processing.
- `FAILED` means retry-waiting; `DEAD` means terminal failure.
- Invalid schedule combinations are rejected before persistence.

## Known Gaps

- No `JobAttempt` table yet.
- No execution-history REST API yet.
- No real job handlers yet; execution is simulated.
- No distributed scheduler leadership yet.
- No metrics dashboard yet, although Actuator metrics are enabled.

## Interview Talking Points

- Why PostgreSQL is the source of truth.
- Why RabbitMQ is a good fit for task dispatch.
- How duplicate messages are handled.
- How publish failure recovery works.
- How worker timeout recovery works.
- How retry state is modeled.
- Why `FAILED` and `DEAD` are separate states.
- What would be needed to run multiple scheduler instances safely.
