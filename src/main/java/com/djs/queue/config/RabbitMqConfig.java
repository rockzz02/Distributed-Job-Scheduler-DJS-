package com.djs.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Bean
    public DirectExchange jobExchange(RabbitMqTopologyProperties properties) {
        return ExchangeBuilder.directExchange(properties.jobExchange())
                .durable(true)
                .build();
    }

    @Bean
    public Queue jobQueue(RabbitMqTopologyProperties properties) {
        return QueueBuilder.durable(properties.jobQueue())
                .withArgument("x-dead-letter-exchange", properties.deadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding jobQueueBinding(
            @Qualifier("jobQueue") Queue jobQueue,
            @Qualifier("jobExchange") DirectExchange jobExchange,
            RabbitMqTopologyProperties properties
    ) {
        return BindingBuilder.bind(jobQueue)
                .to(jobExchange)
                .with(properties.jobRoutingKey());
    }

    @Bean
    public DirectExchange deadLetterExchange(RabbitMqTopologyProperties properties) {
        return ExchangeBuilder.directExchange(properties.deadLetterExchange())
                .durable(true)
                .build();
    }

    @Bean
    public Queue deadLetterQueue(RabbitMqTopologyProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    public Binding deadLetterQueueBinding(
            @Qualifier("deadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
            RabbitMqTopologyProperties properties
    ) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
