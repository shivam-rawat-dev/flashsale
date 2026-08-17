package com.enterprise.flashsale.service;

import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import com.enterprise.flashsale.entity.Order;
import com.enterprise.flashsale.entity.Order.OrderStatus;
import com.enterprise.flashsale.entity.Reservation;
import com.enterprise.flashsale.entity.Reservation.ReservationStatus;
import com.enterprise.flashsale.exception.ResourceNotFoundException;
import com.enterprise.flashsale.repository.InventoryRepository;
import com.enterprise.flashsale.repository.OrderRepository;
import com.enterprise.flashsale.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String RESERVATION_KEY_PREFIX = "flashsale:reservation:";
    private static final String USER_HOLD_KEY_PREFIX = "reservation:user:";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;

    @Value("${app.kafka.topics.flashsale-orders:flashsale-orders}")
    private String flashSaleOrdersTopic;

    /**
     * Validates reservation hold and publishes order event for asynchronous fulfillment.
     */
    public String checkout(String reservationId, Long userId, Long itemId, BigDecimal amount) {
        String reservationKey = RESERVATION_KEY_PREFIX + reservationId;

        // 1. Validate active reservation in Redis
        String reservationData = redisTemplate.opsForValue().get(reservationKey);
        if (reservationData == null) {
            throw new IllegalArgumentException("Reservation is invalid or has expired. ID: " + reservationId);
        }

        String[] parts = reservationData.split(":");
        Long reservedUserId = Long.parseLong(parts[0]);
        Long reservedProductId = Long.parseLong(parts[1]);
        Integer reservedQuantity = Integer.parseInt(parts[2]);

        if (!reservedUserId.equals(userId) || !reservedProductId.equals(itemId)) {
            throw new IllegalArgumentException("Reservation payload mismatch with caller parameters.");
        }

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        FlashSaleOrderEvent orderEvent = FlashSaleOrderEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .productId(itemId)
                .quantity(reservedQuantity)
                .price(amount)
                .timestamp(System.currentTimeMillis())
                .build();

        // 2. Dispatch to Kafka first before deleting Redis key
        kafkaTemplate.send(flashSaleOrdersTopic, orderId, orderEvent);

        // 3. Clean up Redis checkout key
        redisTemplate.delete(reservationKey);

        log.info("Order {} dispatched to Kafka for reservation {}", orderId, reservationId);
        return orderId;
    }

    /**
     * Synchronous payment settlement using atomic DB update queries.
     */
    @Transactional
    public void confirmPaymentSuccess(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getPaymentStatus() == OrderStatus.PAID) {
            return; // Idempotent exit
        }

        Reservation reservation = reservationRepository.findById(order.getReservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + order.getReservationId()));

        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            order.setPaymentStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            throw new IllegalStateException("Cannot settle payment: reservation has expired.");
        }

        // Execute atomic SQL deduction
        int updatedRows = inventoryRepository.confirmDeduction(order.getProductId(), order.getQuantity());
        if (updatedRows == 0) {
            throw new IllegalStateException("Failed to deduct DB inventory. Stock inconsistency detected.");
        }

        order.setPaymentStatus(OrderStatus.PAID);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        orderRepository.save(order);
        reservationRepository.save(reservation);

        // Remove user rate-limit hold key
        redisTemplate.delete(USER_HOLD_KEY_PREFIX + order.getUserId() + ":" + order.getProductId());
    }
}