package com.enterprise.flashsale.service;

import com.enterprise.flashsale.dto.FlashSaleOrderEvent;
import com.enterprise.flashsale.dto.request.ReservationRequest;
import com.enterprise.flashsale.dto.response.ReservationResponse;
import com.enterprise.flashsale.entity.Reservation;
import com.enterprise.flashsale.entity.Reservation.ReservationStatus;
import com.enterprise.flashsale.entity.TransactionalOutbox;
import com.enterprise.flashsale.exception.DuplicateReservationException;
import com.enterprise.flashsale.exception.SoldOutException;
import com.enterprise.flashsale.repository.OutboxRepository;
import com.enterprise.flashsale.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final StringRedisTemplate redisTemplate;
    private final ReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private DefaultRedisScript<Long> reservationScript;

    @PostConstruct
    public void init() {
        reservationScript = new DefaultRedisScript<>();
        reservationScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/stock_deduct.lua")));
        reservationScript.setResultType(Long.class);
    }

    @Transactional
    public ReservationResponse reserveStock(Long userId, ReservationRequest request) {
        String reservationId = "RES-" + UUID.randomUUID();
        String inventoryKey = "inventory:" + request.getProductId();
        String reservationKey = "reservation:" + userId + ":" + request.getProductId();

        List<String> keys = List.of(inventoryKey, reservationKey);
        Object[] args = new Object[]{
                String.valueOf(request.getQuantity()),
                String.valueOf(600), // 10-minute hold TTL
                reservationId
        };

        Long result = redisTemplate.execute(reservationScript, keys, args);

        if (result == null || result == -1L) {
            throw new IllegalStateException("Product inventory not initialized in cache");
        } else if (result == 0L) {
            throw new SoldOutException("Flash sale item is sold out or insufficient stock remaining");
        } else if (result == -2L) {
            throw new DuplicateReservationException("User already holds an active reservation for this product");
        }

        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(600);

            // 1. Save Reservation entity
            Reservation reservation = Reservation.builder()
                    .reservationId(reservationId)
                    .userId(userId)
                    .itemId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status(ReservationStatus.RESERVED)
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .build();
            reservationRepository.save(reservation);

            // 2. Build Event matching FlashSaleOrderEvent schema
            FlashSaleOrderEvent orderEvent = FlashSaleOrderEvent.builder()
                    .orderId(reservationId) // Carries reservation reference for downstream consumers
                    .productId(request.getProductId())
                    .userId(userId)
                    .quantity(request.getQuantity())
                    .price(BigDecimal.ZERO) // Populated by pricing service or set during order stage
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 3. Save to Transactional Outbox
            TransactionalOutbox outbox = TransactionalOutbox.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType("RESERVATION")
                    .aggregateId(reservationId)
                    .eventType("RESERVATION_CREATED")
                    .payload(objectMapper.writeValueAsString(orderEvent))
                    .processed(false)
                    .createdAt(now)
                    .build();
            outboxRepository.save(outbox);

            // 4. Return matching ReservationResponse
            return ReservationResponse.builder()
                    .reservationId(reservationId)
                    .userId(userId)
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .status(ReservationStatus.RESERVED.name())
                    .expiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                    .message("Inventory reserved successfully. Please complete payment within 10 minutes.")
                    .build();

        } catch (Exception e) {
            log.error("Database save failed for reservation {}. Compensating Redis hold.", reservationId, e);
            // Compensate Redis
            redisTemplate.opsForValue().increment(inventoryKey, request.getQuantity());
            redisTemplate.delete(reservationKey);
            throw new RuntimeException("Failed to persist reservation", e);
        }
    }

    @Transactional
    public void processTimeout(String reservationId, Long productId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("Reservation {} not found during timeout processing", reservationId);
            return;
        }

        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);

            // Restore Redis stock & clear user hold
            String inventoryKey = "inventory:" + productId;
            String reservationKey = "reservation:" + reservation.getUserId() + ":" + productId;

            redisTemplate.opsForValue().increment(inventoryKey, reservation.getQuantity());
            redisTemplate.delete(reservationKey);

            log.info("Released {} units for product {} from expired reservation {}",
                    reservation.getQuantity(), productId, reservationId);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservationTimeoutEvent implements Serializable {
        private String reservationId;
        private Long productId;
    }
}