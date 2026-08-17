package com.enterprise.flashsale.consumer;

import com.enterprise.flashsale.config.RabbitMQConfig;
import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import com.enterprise.flashsale.entity.Order;
import com.enterprise.flashsale.repository.OrderRepository;
import com.enterprise.flashsale.service.InventoryReservationService.ReservationTimeoutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleOrderConsumer {

    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.ORDER_PROCESSING_QUEUE)
    public void processFlashSaleOrder(String messagePayload) {
        String idempotencyKey = null;
        try {
            FlashSaleOrderEvent event = objectMapper.readValue(messagePayload, FlashSaleOrderEvent.class);
            String reservationId = event.getOrderId(); // Carries the reservationId from outbox
            idempotencyKey = "idempotency:order:" + reservationId;

            // 1. Redis Distributed Idempotency Lock
            Boolean isFirstProcessing = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, "PROCESSING", Duration.ofMinutes(15));

            if (Boolean.FALSE.equals(isFirstProcessing)) {
                log.warn("Duplicate message detected for reservationId: {}. Skipping.", reservationId);
                return;
            }

            // 2. DB Idempotency Check
            if (orderRepository.existsByReservationId(reservationId)) {
                log.warn("Order already exists in DB for reservationId: {}. Skipping.", reservationId);
                redisTemplate.opsForValue().set(idempotencyKey, "COMPLETED", Duration.ofHours(24));
                return;
            }

            // 3. Persist Order Entity
            BigDecimal unitPrice = event.getPrice() != null ? event.getPrice() : BigDecimal.ZERO;
            BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(event.getQuantity()));

            String orderId = "ORD-" + UUID.randomUUID();
            Order order = Order.builder()
                    .orderId(orderId)
                    .reservationId(reservationId)
                    .userId(event.getUserId())
                    .productId(event.getProductId())
                    .quantity(event.getQuantity())
                    .totalAmount(totalAmount)
                    .paymentStatus(Order.OrderStatus.PENDING)
                    .build();

            orderRepository.save(order);

            // 4. Send timeout event to RabbitMQ TTL hold queue
            ReservationTimeoutEvent timeoutEvent = ReservationTimeoutEvent.builder()
                    .reservationId(reservationId)
                    .productId(event.getProductId())
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FLASH_SALE_EXCHANGE,
                    RabbitMQConfig.RESERVATION_HOLD_ROUTING_KEY,
                    objectMapper.writeValueAsString(timeoutEvent)
            );

            // 5. Mark idempotency key as completed
            redisTemplate.opsForValue().set(idempotencyKey, "COMPLETED", Duration.ofHours(24));
            log.info("Successfully created order {} for reservation {}", orderId, reservationId);

        } catch (Exception e) {
            if (idempotencyKey != null) {
                redisTemplate.delete(idempotencyKey);
            }
            log.error("Failed processing order event payload: {}", messagePayload, e);
            throw new RuntimeException("Message processing failed, requeueing", e);
        }
    }
}