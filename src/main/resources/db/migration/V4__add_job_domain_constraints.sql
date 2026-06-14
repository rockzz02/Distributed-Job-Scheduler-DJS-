alter table jobs
    add constraint chk_jobs_name_not_blank
        check (btrim(name) <> '');

alter table jobs
    add constraint chk_jobs_type_not_blank
        check (btrim(type) <> '');

alter table jobs
    add constraint chk_jobs_payload_object
        check (jsonb_typeof(payload) = 'object');

alter table jobs
    add constraint chk_jobs_status
        check (status in ('ACTIVE', 'PAUSED', 'COMPLETED', 'DELETED'));

alter table jobs
    add constraint chk_jobs_schedule_type
        check (schedule_type in ('ONE_TIME', 'FIXED_RATE', 'CRON'));

alter table jobs
    add constraint chk_jobs_retry_strategy
        check (retry_strategy in ('NONE', 'FIXED', 'EXPONENTIAL'));

alter table jobs
    add constraint chk_jobs_schedule_fields
        check (
            (
                schedule_type = 'ONE_TIME'
                and interval_seconds is null
                and cron_expression is null
            )
            or (
                schedule_type = 'FIXED_RATE'
                and interval_seconds is not null
                and cron_expression is null
            )
            or (
                schedule_type = 'CRON'
                and interval_seconds is null
                and cron_expression is not null
                and btrim(cron_expression) <> ''
            )
        );

alter table jobs
    add constraint chk_jobs_none_retry_has_no_retries
        check (retry_strategy <> 'NONE' or max_retries = 0);
