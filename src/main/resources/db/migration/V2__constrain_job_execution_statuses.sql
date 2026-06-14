update job_executions
set status = 'PENDING'
where status in ('SCHEDULED', 'QUEUED');

update job_executions
set status = 'SUCCESS'
where status = 'SUCCEEDED';

alter table job_executions
    add constraint chk_job_executions_status
        check (status in ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED'));
