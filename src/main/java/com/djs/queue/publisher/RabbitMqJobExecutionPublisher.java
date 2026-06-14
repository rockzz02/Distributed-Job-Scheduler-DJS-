package com.djs.queue.publisher;

import com.djs.queue.config.RabbitMqTopologyProperties;
import com.djs.queue.message.JobExecutionMessage;
import com.djs.common.logging.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqJobExecutionPublisher implements JobExecutionPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqJobExecutionPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqTopologyProperties rabbitMqTopologyProperties;

    public RabbitMqJobExecutionPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMqTopologyProperties rabbitMqTopologyProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMqTopologyProperties = rabbitMqTopologyProperties;
    }

    @Override
    public void publish(JobExecutionMessage message) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.TRACE_ID, message.traceId().toString())) {
            rabbitTemplate.convertAndSend(
                    rabbitMqTopologyProperties.jobExchange(),
                    rabbitMqTopologyProperties.jobRoutingKey(),
                    message
            );
            log.info(
                    "Published job execution message messageId={} jobId={} executionId={}",
                    message.messageId(),
                    message.jobId(),
                    message.executionId()
            );
        }
    }
}
