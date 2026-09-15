package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.dto.request.CheckoutRequest;
import com.enterprise.flashsale.dto.request.PaymentCallbackRequest;
import com.enterprise.flashsale.entity.AppUser;
import com.enterprise.flashsale.entity.Order;
import com.enterprise.flashsale.repository.UserRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Processing", description = "Endpoints for order checkout and payment confirmation")
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

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
            Authentication authentication,
            @Parameter(description = "User ID placing the order", example = "1001")
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @Valid @RequestBody CheckoutRequest request) {

        Long effectiveUserId = null;
        if (request.userId() != null) {
            effectiveUserId = request.userId();
        } else if (headerUserId != null) {
            effectiveUserId = headerUserId;
        } else if (authentication != null && authentication.getName() != null) {
            effectiveUserId = userRepository.findByEmail(authentication.getName())
                    .map(AppUser::getId)
                    .orElse(null);
        }

        if (effectiveUserId == null) {
            effectiveUserId = 1001L;
        }

        String orderId = orderService.checkout(
                request.reservationId(),
                effectiveUserId,
                request.itemId(),
                request.amount()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Order successfully placed and submitted for processing",
                "orderId", orderId,
                "status", "SUCCESS"
        ));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details", description = "Retrieves current order status and details by order ID.")
    public ResponseEntity<Order> getOrder(
            @Parameter(description = "Unique order identifier", required = true, example = "ORD-12345678")
            @PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/payment-success")
    @Idempotent(ttlMinutes = 15)
    @Operation(summary = "Confirm payment success", description = "Webhook / callback handler when payment is confirmed for an order.")
    public ResponseEntity<Map<String, Object>> paymentSuccess(
            @Parameter(description = "Unique order identifier", required = true, example = "ORD-12345678")
            @PathVariable String orderId) {
        orderService.confirmPaymentSuccess(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "PAID",
                "message", "Payment settled and inventory finalized"
        ));
    }

    @PostMapping("/{orderId}/payment-failed")
    @Idempotent(ttlMinutes = 15)
    @Operation(summary = "Handle payment failure / cancellation", description = "Rolls back reserved inventory and updates order status to CANCELLED.")
    public ResponseEntity<Map<String, Object>> paymentFailed(
            @Parameter(description = "Unique order identifier", required = true, example = "ORD-12345678")
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Payment authorization rejected by bank") String reason) {
        orderService.handlePaymentFailure(orderId, reason);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "CANCELLED",
                "message", "Payment cancelled. Reserved inventory successfully restored to pool."
        ));
    }

    @PostMapping("/webhook")
    @Idempotent(ttlMinutes = 15)
    @Operation(summary = "Unified Payment Gateway Webhook", description = "Receives async webhook notifications from payment providers (Stripe/Razorpay).")
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(
            @Valid @RequestBody PaymentCallbackRequest callbackRequest) {

        String orderId = callbackRequest.getTransactionReference();
        if (callbackRequest.getStatus() == PaymentCallbackRequest.PaymentResultStatus.SUCCESS) {
            orderService.confirmPaymentSuccess(orderId);
            return ResponseEntity.ok(Map.of(
                    "transactionReference", orderId,
                    "status", "PAID",
                    "message", "Webhook processed: payment confirmed"
            ));
        } else {
            orderService.handlePaymentFailure(orderId, "Payment webhook reported failure for paymentId: " + callbackRequest.getPaymentId());
            return ResponseEntity.ok(Map.of(
                    "transactionReference", orderId,
                    "status", "CANCELLED",
                    "message", "Webhook processed: stock restored"
            ));
        }
    }
}
