package com.djs.queue.consumer;

import java.io.IOException;

import com.djs.common.logging.MdcKeys;
import com.djs.queue.message.JobExecutionMessage;
import com.djs.worker.executor.JobExecutionWorker;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "djs.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobExecutionConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionConsumer.class);

    private final JobExecutionWorker jobExecutionWorker;

    public JobExecutionConsumer(JobExecutionWorker jobExecutionWorker) {
        this.jobExecutionWorker = jobExecutionWorker;
    }

    @RabbitListener(
            queues = "${djs.rabbitmq.job-queue}",
            concurrency = "${djs.worker.concurrency:4}"
    )
    public void consume(JobExecutionMessage payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.TRACE_ID, payload.traceId().toString())) {
            log.info(
                    "Received job execution message messageId={} jobId={} executionId={}",
                    payload.messageId(),
                    payload.jobId(),
                    payload.executionId()
            );
            jobExecutionWorker.process(payload);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error(
                    "Worker failed while processing message messageId={} executionId={}",
                    payload.messageId(),
                    payload.executionId(),
                    exception
            );
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
