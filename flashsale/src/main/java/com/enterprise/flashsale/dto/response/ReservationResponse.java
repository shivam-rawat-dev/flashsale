package com.enterprise.flashsale.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private String reservationId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private String status;
    private LocalDateTime expiresAt;
    private String message;
}