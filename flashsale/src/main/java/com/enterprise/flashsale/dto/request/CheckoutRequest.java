package com.enterprise.flashsale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CheckoutRequest(
        @NotBlank(message = "Reservation ID is required") String reservationId,
        @NotNull(message = "Item ID is required") Long itemId,
        @NotNull(message = "Amount is required") BigDecimal amount
) {}