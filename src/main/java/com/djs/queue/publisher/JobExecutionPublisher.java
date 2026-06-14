package com.djs.queue.publisher;

import com.djs.queue.message.JobExecutionMessage;

public interface JobExecutionPublisher {

    void publish(JobExecutionMessage message);
}
