package com.enterprise.flashsale.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String FLASH_SALE_EXCHANGE = "flashsale.direct.exchange";
    public static final String DLX_EXCHANGE = "flashsale.dlx.exchange";

    public static final String ORDER_PROCESSING_QUEUE = "flashsale.order.queue";
    public static final String RESERVATION_HOLD_TTL_QUEUE = "flashsale.reservation.hold.ttl.queue";
    public static final String RESERVATION_TIMEOUT_QUEUE = "flashsale.reservation.timeout.queue";

    public static final String ORDER_ROUTING_KEY = "order.create";
    public static final String RESERVATION_HOLD_ROUTING_KEY = "reservation.hold";
    public static final String RESERVATION_TIMEOUT_ROUTING_KEY = "reservation.timeout";

    @Bean
    public DirectExchange flashSaleExchange() {
        return new DirectExchange(FLASH_SALE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderProcessingQueue() {
        return QueueBuilder.durable(ORDER_PROCESSING_QUEUE).build();
    }

    // TTL Queue without consumers: dead-letters messages to DLX after 10 minutes
    @Bean
    public Queue reservationHoldTtlQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", RESERVATION_TIMEOUT_ROUTING_KEY);
        args.put("x-message-ttl", 600000); // 10 minutes in milliseconds
        return new Queue(RESERVATION_HOLD_TTL_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue reservationTimeoutQueue() {
        return QueueBuilder.durable(RESERVATION_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderProcessingQueue()).to(flashSaleExchange()).with(ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding reservationHoldBinding() {
        return BindingBuilder.bind(reservationHoldTtlQueue()).to(flashSaleExchange()).with(RESERVATION_HOLD_ROUTING_KEY);
    }

    @Bean
    public Binding reservationTimeoutBinding() {
        return BindingBuilder.bind(reservationTimeoutQueue()).to(dlxExchange()).with(RESERVATION_TIMEOUT_ROUTING_KEY);
    }
}