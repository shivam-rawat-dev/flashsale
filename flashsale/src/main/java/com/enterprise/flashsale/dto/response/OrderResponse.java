package com.enterprise.flashsale.dto.response;

import com.enterprise.flashsale.entity.Order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing order details")
public class OrderResponse {
    @Schema(description = "Unique identifier of the order", example = "ord_123456789")
    private String orderId;

    @Schema(description = "Associated reservation ID", example = "res_123456789")
    private String reservationId;

    @Schema(description = "User ID who placed the order", example = "1001")
    private Long userId;

    @Schema(description = "Product ID", example = "101")
    private Long productId;

    @Schema(description = "Quantity ordered", example = "1")
    private Integer quantity;

    @Schema(description = "Total price amount", example = "499.99")
    private BigDecimal totalAmount;

    @Schema(description = "Current payment status of the order")
    private OrderStatus paymentStatus;

    @Schema(description = "Timestamp when the order was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the order was last updated")
    private LocalDateTime updatedAt;
}
