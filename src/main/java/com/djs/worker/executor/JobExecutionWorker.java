package com.djs.worker.executor;

import com.djs.queue.message.JobExecutionMessage;

public interface JobExecutionWorker {

    void process(JobExecutionMessage message);
}
