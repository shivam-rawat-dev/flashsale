package com.enterprise.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleOrderEvent implements Serializable {
    private String orderId;
    private Long productId;
    private Long userId;
    private Integer quantity;
    private BigDecimal price;
    private Long timestamp;
}