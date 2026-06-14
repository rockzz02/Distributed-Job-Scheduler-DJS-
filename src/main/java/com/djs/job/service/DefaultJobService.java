package com.djs.job.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;

import com.djs.job.model.Job;
import com.djs.job.model.JobStatus;
import com.djs.job.repository.JobRepository;
import com.djs.job.validation.JobCommandValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultJobService implements JobService {

    private static final Logger log = LoggerFactory.getLogger(DefaultJobService.class);

    private final JobRepository jobRepository;
    private final JobCommandValidator jobCommandValidator;

    public DefaultJobService(JobRepository jobRepository, JobCommandValidator jobCommandValidator) {
        this.jobRepository = jobRepository;
        this.jobCommandValidator = jobCommandValidator;
    }

    @Override
    @Transactional
    public Job createJob(CreateJobCommand command) {
        jobCommandValidator.validateCreate(command);

        Job job = new Job();
        job.setName(command.name());
        job.setDescription(command.description());
        job.setType(normalize(command.type()));
        job.setPayload(new LinkedHashMap<>(command.payload()));
        job.setScheduleType(normalize(command.scheduleType()));
        job.setCronExpression(command.cronExpression());
        job.setIntervalSeconds(command.intervalSeconds());
        job.setNextRunAt(command.nextRunAt());
        job.setStatus(JobStatus.ACTIVE);
        job.setMaxRetries(command.maxRetries());
        job.setRetryStrategy(normalize(command.retryStrategy()));
        job.setRetryDelaySeconds(command.retryDelaySeconds());
        job.setTimeoutSeconds(command.timeoutSeconds());

        Job saved = jobRepository.save(job);
        log.info("Created job id={} name={} type={}", saved.getId(), saved.getName(), saved.getType());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Job> listJobs(Pageable pageable) {
        return jobRepository.findByStatusNot(JobStatus.DELETED, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Job getJob(UUID id) {
        return findVisibleJob(id);
    }

    @Override
    @Transactional
    public void deleteJob(UUID id) {
        Job job = findVisibleJob(id);
        job.setStatus(JobStatus.DELETED);
        jobRepository.save(job);
        log.info("Soft deleted job id={}", id);
    }

    private Job findVisibleJob(UUID id) {
        return jobRepository.findByIdAndStatusNot(id, JobStatus.DELETED)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
