package com.enterprise.flashsale.consumer;

import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import com.enterprise.flashsale.entity.Order;
import com.enterprise.flashsale.entity.Order.OrderStatus;
import com.enterprise.flashsale.repository.InventoryRepository;
import com.enterprise.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleKafkaOrderConsumer {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = "${app.kafka.topics.flashsale-orders:flashsale-orders}",
            groupId = "${spring.kafka.consumer.group-id:flashsale-order-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processFlashSaleOrder(ConsumerRecord<String, FlashSaleOrderEvent> record, Acknowledgment ack) {
        FlashSaleOrderEvent event = record.value();
        String orderId = event.getOrderId();
        String idempotencyKey = "idempotency:kafka:order:" + orderId;

        log.info("Received Kafka order event. OrderId: {}, ProductId: {}, Quantity: {}",
                orderId, event.getProductId(), event.getQuantity());

        try {
            // 1. Redis-level fast idempotency guard (1-hour window)
            Boolean isFirstProcessing = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, "PROCESSING", Duration.ofHours(1));

            if (Boolean.FALSE.equals(isFirstProcessing)) {
                log.warn("Duplicate Kafka message detected for orderId: {}. Skipping.", orderId);
                ack.acknowledge();
                return;
            }

            // 2. Database transaction: Reserve DB inventory & persist Order
            persistOrderAndReserveStock(event);

            // 3. Update Redis idempotency key to COMPLETED
            redisTemplate.opsForValue().set(idempotencyKey, "COMPLETED", Duration.ofHours(24));

            // 4. Manually commit Kafka offset
            ack.acknowledge();
            log.info("Successfully persisted order {} and reserved DB inventory", orderId);

        } catch (Exception e) {
            log.error("Failed to process order event for orderId: {}", orderId, e);

            // Clean up temporary processing lock to allow retry
            redisTemplate.delete(idempotencyKey);

            // Do NOT acknowledge: triggers Kafka redelivery or DLQ handling
            throw new RuntimeException("Error processing Kafka order event. Re-queueing for retry.", e);
        }
    }

    @Transactional
    public void persistOrderAndReserveStock(FlashSaleOrderEvent event) {
        // 1. Secondary DB-level Idempotency Check
        if (orderRepository.existsById(event.getOrderId())) {
            log.warn("Order {} already exists in DB. Skipping duplicate insert.", event.getOrderId());
            return;
        }

        // 2. Atomically decrement availableStock and increment reservedStock in MySQL
        int updatedRows = inventoryRepository.reserveStock(event.getProductId(), event.getQuantity());
        if (updatedRows == 0) {
            log.error("Failed to reserve stock in DB for product: {}. Insufficient available stock or product missing.",
                    event.getProductId());
            throw new IllegalStateException("DB inventory synchronization failed for productId: " + event.getProductId());
        }

        // 3. Calculate total amount
        BigDecimal unitPrice = event.getPrice() != null ? event.getPrice() : BigDecimal.ZERO;
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(event.getQuantity()));

        // 4. Build and persist Order
        Order order = Order.builder()
                .orderId(event.getOrderId())
                .reservationId(event.getOrderId()) // Tracks reservation mapping
                .userId(event.getUserId())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .totalAmount(totalAmount)
                .paymentStatus(OrderStatus.PENDING)
                .build();

        orderRepository.save(order);
    }
}