package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.dto.request.CheckoutRequest;
import com.enterprise.flashsale.security.Idempotent;
import com.enterprise.flashsale.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    @Idempotent(ttlMinutes = 15)
    public ResponseEntity<Map<String, Object>> checkout(
            @RequestHeader(value = "X-User-Id", defaultValue = "1001") Long userId,
            @Valid @RequestBody CheckoutRequest request) {

        String orderId = orderService.checkout(
                request.reservationId(),
                userId,
                request.itemId(),
                request.amount()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Order successfully placed and submitted for processing",
                "orderId", orderId,
                "status", "SUCCESS"
        ));
    }

    @PostMapping("/{orderId}/payment-success")
    @Idempotent(ttlMinutes = 15)

    public ResponseEntity<Map<String, Object>> paymentSuccess(@PathVariable String orderId) {
        orderService.confirmPaymentSuccess(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "PAID",
                "message", "Payment settled and inventory finalized"
        ));
    }


}