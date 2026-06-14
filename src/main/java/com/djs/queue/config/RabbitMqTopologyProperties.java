package com.djs.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "djs.rabbitmq")
public record RabbitMqTopologyProperties(
        String jobExchange,
        String jobQueue,
        String jobRoutingKey,
        String deadLetterExchange,
        String deadLetterQueue,
        String deadLetterRoutingKey
) {
}
