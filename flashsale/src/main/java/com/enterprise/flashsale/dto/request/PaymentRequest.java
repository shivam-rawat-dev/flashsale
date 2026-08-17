package com.enterprise.flashsale.dto.request;

import java.math.BigDecimal;

public record PaymentRequest(
        String orderId,
        BigDecimal amount,
        String currency
) {}