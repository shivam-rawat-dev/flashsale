package com.enterprise.flashsale.service;

import com.enterprise.flashsale.dto.request.ReservationRequest;
import com.enterprise.flashsale.dto.response.ReservationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private static final String RESERVATION_KEY_PREFIX = "flashsale:reservation:";
    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Executes Redis Lua pre-deduction and creates a temporary 10-minute reservation lock.
     */
    public ReservationResponse reserveInventory(Long userId, ReservationRequest request) {
        // 1. High-speed stock reservation via Lua script
        boolean reserved = inventoryService.preDeductStock(request.getProductId(), request.getQuantity());

        if (!reserved) {
            throw new IllegalStateException("Failed to reserve stock: Item is sold out or insufficient quantity remaining.");
        }

        // 2. Generate temporary reservation token and store details in Redis (10-minute window to checkout)
        String reservationId = UUID.randomUUID().toString();
        String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
        String reservationVal = userId + ":" + request.getProductId() + ":" + request.getQuantity();

        redisTemplate.opsForValue().set(reservationKey, reservationVal, Duration.ofMinutes(10));

        log.info("Inventory reserved successfully. ReservationId: {}, UserId: {}, ProductId: {}",
                reservationId, userId, request.getProductId());

        return ReservationResponse.builder()
                .reservationId(reservationId)
                .userId(userId)
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status("RESERVED")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .message("Inventory reserved successfully. Complete checkout within 10 minutes.")
                .build();
    }
}