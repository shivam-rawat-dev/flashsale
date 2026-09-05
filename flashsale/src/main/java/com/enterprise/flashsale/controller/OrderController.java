package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.dto.request.CheckoutRequest;
import com.enterprise.flashsale.security.Idempotent;
import com.enterprise.flashsale.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Processing", description = "Endpoints for order checkout and payment confirmation")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    @Idempotent(ttlMinutes = 15)
    @Operation(summary = "Checkout order", description = "Converts an active reservation into an order, persists outbox event, and initiates payment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order created and queued for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid request or expired reservation"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires USER or ADMIN role")
    })
    public ResponseEntity<Map<String, Object>> checkout(
            @Parameter(description = "User ID placing the order", example = "1001")
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
    @Operation(summary = "Confirm payment success", description = "Webhook / callback handler when payment is confirmed for an order.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment settled and inventory finalized"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires USER or ADMIN role")
    })
    public ResponseEntity<Map<String, Object>> paymentSuccess(
            @Parameter(description = "Unique order identifier", required = true, example = "ord_123456789")
            @PathVariable String orderId) {
        orderService.confirmPaymentSuccess(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "PAID",
                "message", "Payment settled and inventory finalized"
        ));
    }
}
