package com.djs.job.service;

import java.util.UUID;

import com.djs.job.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobService {

    Job createJob(CreateJobCommand command);

    Page<Job> listJobs(Pageable pageable);

    Job getJob(UUID id);

    void deleteJob(UUID id);
}
