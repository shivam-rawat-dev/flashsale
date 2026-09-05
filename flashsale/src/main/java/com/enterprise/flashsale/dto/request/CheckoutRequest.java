package com.enterprise.flashsale.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request payload to checkout an active reservation")
public record CheckoutRequest(
        @NotBlank(message = "Reservation ID is required")
        @Schema(description = "Active reservation ID", example = "res_987654321", requiredMode = Schema.RequiredMode.REQUIRED)
        String reservationId,

        @NotNull(message = "Item ID is required")
        @Schema(description = "Product / item ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        Long itemId,

        @NotNull(message = "Amount is required")
        @Schema(description = "Total purchase amount", example = "499.99", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount
) {}
