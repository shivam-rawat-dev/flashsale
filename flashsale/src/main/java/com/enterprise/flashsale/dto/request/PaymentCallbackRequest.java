package com.enterprise.flashsale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackRequest {

    @NotBlank(message = "Payment ID is required")
    private String paymentId;

    @NotBlank(message = "Payment reference / transaction ID is required")
    private String transactionReference;

    @NotNull(message = "Payment status is required")
    private PaymentResultStatus status;

    private BigDecimal amountPaid;

    public enum PaymentResultStatus {
        SUCCESS,
        FAILED
    }
}