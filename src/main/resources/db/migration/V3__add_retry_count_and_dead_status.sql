alter table job_executions
    add column retry_count integer not null default 0;

alter table job_executions
    add constraint chk_job_executions_retry_count_non_negative check (retry_count >= 0);

alter table job_executions
    drop constraint if exists chk_job_executions_status;

alter table job_executions
    add constraint chk_job_executions_status
        check (status in ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD'));
