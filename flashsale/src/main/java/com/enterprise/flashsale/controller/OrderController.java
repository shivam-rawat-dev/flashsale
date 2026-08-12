package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
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
                "message", "Order successfully placed and finalized",
                "orderId", orderId,
                "status", "SUCCESS"
        ));
    }

    public record CheckoutRequest(
            @NotBlank String reservationId,
            @NotNull Long itemId,
            @NotNull BigDecimal amount
    ) {}
}