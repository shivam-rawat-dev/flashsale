package com.enterprise.flashsale.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing reservation details")
public class ReservationResponse {

    @Schema(description = "Unique identifier of the reservation", example = "res_123456789")
    private String reservationId;

    @Schema(description = "ID of the reserving user", example = "1001")
    private Long userId;

    @Schema(description = "ID of the reserved product", example = "101")
    private Long productId;

    @Schema(description = "Quantity reserved", example = "1")
    private Integer quantity;

    @Schema(description = "Status of the reservation (e.g. PENDING, CONFIRMED, EXPIRED)", example = "PENDING")
    private String status;

    @Schema(description = "Expiry timestamp for the reservation hold")
    private LocalDateTime expiresAt;

    @Schema(description = "Status or informational message", example = "Stock held successfully for 5 minutes")
    private String message;
}
