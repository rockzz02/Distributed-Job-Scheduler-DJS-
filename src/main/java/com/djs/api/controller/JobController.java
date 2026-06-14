package com.djs.api.controller;

import java.net.URI;
import java.util.UUID;

import com.djs.api.mapper.JobApiMapper;
import com.djs.api.request.CreateJobRequest;
import com.djs.api.response.JobResponse;
import com.djs.job.model.Job;
import com.djs.job.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;
    private final JobApiMapper jobApiMapper;

    public JobController(JobService jobService, JobApiMapper jobApiMapper) {
        this.jobService = jobService;
        this.jobApiMapper = jobApiMapper;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        Job saved = jobService.createJob(jobApiMapper.toCommand(request));
        JobResponse response = jobApiMapper.toResponse(saved);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public Page<JobResponse> listJobs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return jobService.listJobs(pageable).map(jobApiMapper::toResponse);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobApiMapper.toResponse(jobService.getJob(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
}
