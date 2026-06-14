package com.djs.api.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import com.djs.api.request.CreateJobRequest;
import com.djs.api.response.JobResponse;
import com.djs.job.model.Job;
import com.djs.job.service.CreateJobCommand;
import org.springframework.stereotype.Component;

@Component
public class JobApiMapper {

    public CreateJobCommand toCommand(CreateJobRequest request) {
        return new CreateJobCommand(
                request.name(),
                request.description(),
                request.type(),
                copyPayload(request.payload()),
                request.scheduleType(),
                request.cronExpression(),
                request.intervalSeconds(),
                request.nextRunAt(),
                request.maxRetries(),
                request.retryStrategy(),
                request.retryDelaySeconds(),
                request.timeoutSeconds()
        );
    }

    public JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getDescription(),
                job.getType(),
                copyPayload(job.getPayload()),
                job.getScheduleType(),
                job.getCronExpression(),
                job.getIntervalSeconds(),
                job.getNextRunAt(),
                job.getStatus(),
                job.getMaxRetries(),
                job.getRetryStrategy(),
                job.getRetryDelaySeconds(),
                job.getTimeoutSeconds(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private Map<String, Object> copyPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(payload);
    }
}
