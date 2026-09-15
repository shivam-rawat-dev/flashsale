package com.enterprise.flashsale.service;

import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import com.enterprise.flashsale.entity.Order;
import com.enterprise.flashsale.entity.Order.OrderStatus;
import com.enterprise.flashsale.entity.Reservation;
import com.enterprise.flashsale.entity.Reservation.ReservationStatus;
import com.enterprise.flashsale.exception.ResourceNotFoundException;
import com.enterprise.flashsale.metrics.FlashSaleMetrics;
import com.enterprise.flashsale.repository.InventoryRepository;
import com.enterprise.flashsale.repository.OrderRepository;
import com.enterprise.flashsale.repository.ReservationRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final FlashSaleMetrics metrics;

    @Value("${app.kafka.topics.flashsale-orders:flashsale-orders}")
    private String flashSaleOrdersTopic;

    /**
     * Retrieves an order by ID.
     */
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    /**
     * Validates reservation hold, persists Order entity as PENDING, and dispatches to Kafka for downstream fulfillment.
     */
    @Transactional
    public String checkout(String reservationId, Long userId, Long itemId, BigDecimal amount) {
        Timer.Sample sample = metrics.startCheckoutTimer();
        try {
            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;

            // 1. Validate active reservation in Redis
            String reservationData = redisTemplate.opsForValue().get(reservationKey);
            if (reservationData == null) {
                // Fallback check PostgreSQL if Redis key expired
                Reservation dbRes = reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new IllegalArgumentException("Reservation is invalid or has expired. ID: " + reservationId));
                if (dbRes.getStatus() != ReservationStatus.RESERVED) {
                    throw new IllegalArgumentException("Reservation is no longer active. Status: " + dbRes.getStatus());
                }
            }

            String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            BigDecimal totalAmount = amount != null ? amount : BigDecimal.valueOf(49.99);

            // 2. Synchronously persist Order entity as PENDING
            Order order = Order.builder()
                    .orderId(orderId)
                    .reservationId(reservationId)
                    .userId(userId)
                    .productId(itemId)
                    .quantity(1)
                    .totalAmount(totalAmount)
                    .paymentStatus(OrderStatus.PENDING)
                    .build();
            orderRepository.save(order);

            // 3. Dispatch FlashSaleOrderEvent to Kafka
            FlashSaleOrderEvent orderEvent = FlashSaleOrderEvent.builder()
                    .orderId(orderId)
                    .reservationId(reservationId)
                    .userId(userId)
                    .productId(itemId)
                    .quantity(1)
                    .price(totalAmount)
                    .timestamp(System.currentTimeMillis())
                    .build();

            kafkaTemplate.send(flashSaleOrdersTopic, orderId, orderEvent);

            // 4. Clean up Redis checkout key
            redisTemplate.delete(reservationKey);

            metrics.incrementOrderSuccess();
            log.info("Order {} created and dispatched to Kafka for reservation {}", orderId, reservationId);
            return orderId;
        } finally {
            metrics.stopCheckoutTimer(sample);
        }
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
            metrics.incrementPaymentFailure();
            throw new IllegalStateException("Cannot settle payment: reservation has expired.");
        }

        // Execute atomic SQL deduction
        int updatedRows = inventoryRepository.confirmDeduction(order.getProductId(), order.getQuantity());
        if (updatedRows == 0) {
            log.warn("DB inventory already deducted or adjusted for product: {}", order.getProductId());
        }

        order.setPaymentStatus(OrderStatus.PAID);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        orderRepository.save(order);
        reservationRepository.save(reservation);

        // Remove user rate-limit hold key
        redisTemplate.delete(USER_HOLD_KEY_PREFIX + order.getUserId() + ":" + order.getProductId());
        metrics.incrementPaymentSuccess();
        log.info("Payment settled and confirmed for order {}", orderId);
    }

    /**
     * Handles payment failure or user cancellation by rolling back reserved stock.
     */
    @Transactional
    public void handlePaymentFailure(String orderId, String failureReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getPaymentStatus() == OrderStatus.CANCELLED || order.getPaymentStatus() == OrderStatus.PAID) {
            return; // Idempotent exit
        }

        order.setPaymentStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Update reservation to RELEASED
        reservationRepository.findById(order.getReservationId()).ifPresent(reservation -> {
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        });

        // 1. Release reserved stock back to available stock in DB
        inventoryRepository.releaseReservedStock(order.getProductId(), order.getQuantity());

        // 2. Replenish Redis cache
        String inventoryKey = "inventory:" + order.getProductId();
        redisTemplate.opsForValue().increment(inventoryKey, order.getQuantity());

        // 3. Clear user hold key in Redis
        redisTemplate.delete(USER_HOLD_KEY_PREFIX + order.getUserId() + ":" + order.getProductId());

        metrics.incrementPaymentFailure();
        log.warn("Payment failed for order {}: {}. Successfully rolled back {} stock units for product {}.",
                orderId, failureReason, order.getQuantity(), order.getProductId());
    }
}
