package com.enterprise.flashsale.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "reservation.delay.exchange";
    public static final String QUEUE_NAME = "reservation.timeout.queue";
    public static final String ROUTING_KEY = "reservation.timeout.key";

    @Bean
    public CustomExchange delayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(EXCHANGE_NAME, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public Binding binding(Queue timeoutQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(timeoutQueue)
                .to(delayedExchange)
                .with(ROUTING_KEY)
                .noargs();
    }
}