alter table job_executions
    add constraint chk_job_executions_running_claim
        check (
            status <> 'RUNNING'
            or (
                started_at is not null
                and locked_by is not null
                and locked_until is not null
            )
        );

alter table job_executions
    add constraint chk_job_executions_terminal_completed
        check (status not in ('SUCCESS', 'DEAD') or completed_at is not null);

alter table job_executions
    add constraint chk_job_executions_dead_has_no_next_retry
        check (status <> 'DEAD' or next_retry_at is null);
