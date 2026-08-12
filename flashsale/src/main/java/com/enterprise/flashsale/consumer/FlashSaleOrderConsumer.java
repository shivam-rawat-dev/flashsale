package com.enterprise.flashsale.consumer;

import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import com.enterprise.flashsale.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleOrderConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "flashsale:order:processed:";

    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = "${app.kafka.topics.flashsale-orders:flashsale-orders}",
            groupId = "${spring.kafka.consumer.group-id:flashsale-inventory-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderEvent(
            @Payload FlashSaleOrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Consuming event for orderId: {}, productId: {} [partition: {}, offset: {}]",
                event.getOrderId(), event.getProductId(), partition, offset);

        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.getOrderId();

        // 1. Idempotency Check (Prevent duplicate execution due to Kafka rebalances)
        Boolean isFirstAttempt = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "PROCESSED", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isFirstAttempt)) {
            log.warn("Duplicate order event detected for orderId: {}. Skipping MySQL update.", event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            // 2. Commit stock reservation directly to MySQL
            boolean success = inventoryService.syncReservationToDatabase(
                    event.getProductId(),
                    event.getQuantity()
            );

            if (!success) {
                log.error("Database reservation failed for orderId: {}. Rolling back Redis lock & idempotency key.",
                        event.getOrderId());

                // Remove idempotency key to allow retries if applicable
                redisTemplate.delete(idempotencyKey);

                // Trigger compensating transactional action / Order Failure topic
                handleFailedReservation(event);
            }

            // 3. Manually commit offset on success
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Fatal exception while processing order event for orderId: {}", event.getOrderId(), e);

            // Clean up idempotency state so retry topic/DLQ can re-attempt processing
            redisTemplate.delete(idempotencyKey);

            throw e; // Rethrow to let Spring Kafka ErrorHandler handle retries/DLQ
        }
    }

    private void handleFailedReservation(FlashSaleOrderEvent event) {
        // Send message to dead-letter queue (DLQ) or mark order as FAILED in order DB
        log.warn("Publishing order failure notification for orderId: {}", event.getOrderId());
    }
}