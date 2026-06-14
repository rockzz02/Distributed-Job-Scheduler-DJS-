create table jobs (
    id uuid primary key,
    name varchar(200) not null,
    description varchar(2000),
    type varchar(80) not null,
    payload jsonb not null,
    schedule_type varchar(40) not null,
    cron_expression varchar(120),
    interval_seconds integer,
    next_run_at timestamptz,
    status varchar(40) not null,
    max_retries integer not null,
    retry_strategy varchar(40) not null,
    retry_delay_seconds integer not null,
    timeout_seconds integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    constraint chk_jobs_interval_seconds_positive check (
        interval_seconds is null or interval_seconds > 0
    ),
    constraint chk_jobs_max_retries_non_negative check (max_retries >= 0),
    constraint chk_jobs_retry_delay_seconds_non_negative check (retry_delay_seconds >= 0),
    constraint chk_jobs_timeout_seconds_positive check (timeout_seconds > 0)
);

create index idx_jobs_status_next_run_at on jobs (status, next_run_at);
create index idx_jobs_created_at on jobs (created_at);
create index idx_jobs_type on jobs (type);

create table job_executions (
    id uuid primary key,
    job_id uuid not null,
    scheduled_at timestamptz not null,
    queued_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    status varchar(40) not null,
    attempt_count integer not null,
    next_retry_at timestamptz,
    last_error text,
    locked_by varchar(120),
    locked_until timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    constraint fk_job_executions_job_id foreign key (job_id) references jobs (id),
    constraint chk_job_executions_attempt_count_non_negative check (attempt_count >= 0)
);

create index idx_job_executions_status_scheduled_at on job_executions (status, scheduled_at);
create index idx_job_executions_status_next_retry_at on job_executions (status, next_retry_at);
create index idx_job_executions_job_id_created_at on job_executions (job_id, created_at);
create index idx_job_executions_locked_until on job_executions (locked_until);
