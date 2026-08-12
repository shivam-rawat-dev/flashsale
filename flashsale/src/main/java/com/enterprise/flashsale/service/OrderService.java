package com.enterprise.flashsale.service;

import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String RESERVATION_KEY_PREFIX = "flashsale:reservation:";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, FlashSaleOrderEvent> kafkaTemplate;

    @Value("${app.kafka.topics.flashsale-orders:flashsale-orders}")
    private String flashSaleOrdersTopic;

    /**
     * Validates the active reservation lock and dispatches the order event to Kafka for async MySQL processing.
     */
    public String checkout(String reservationId, Long userId, Long itemId, BigDecimal amount) {
        String reservationKey = RESERVATION_KEY_PREFIX + reservationId;

        // 1. Fetch active reservation from Redis
        String reservationData = redisTemplate.opsForValue().get(reservationKey);
        if (reservationData == null) {
            throw new IllegalArgumentException("Reservation invalid or expired. Reservation ID: " + reservationId);
        }

        // Parse stored user, product, and quantity details
        String[] parts = reservationData.split(":");
        Long reservedUserId = Long.parseLong(parts[0]);
        Long reservedProductId = Long.parseLong(parts[1]);
        Integer reservedQuantity = Integer.parseInt(parts[2]);

        if (!reservedUserId.equals(userId) || !reservedProductId.equals(itemId)) {
            throw new IllegalArgumentException("Reservation details do not match user or item request.");
        }

        // 2. Remove reservation key to prevent double checkout attempts
        redisTemplate.delete(reservationKey);

        // 3. Construct Order ID and event payload
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        FlashSaleOrderEvent orderEvent = FlashSaleOrderEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .productId(itemId)
                .quantity(reservedQuantity)
                .price(amount)
                .timestamp(System.currentTimeMillis())
                .build();

        // 4. Send event to Kafka for asynchronous MySQL persistence
        kafkaTemplate.send(flashSaleOrdersTopic, orderId, orderEvent);

        log.info("Order event successfully sent to Kafka. OrderId: {}, Topic: {}", orderId, flashSaleOrdersTopic);
        return orderId;
    }
}