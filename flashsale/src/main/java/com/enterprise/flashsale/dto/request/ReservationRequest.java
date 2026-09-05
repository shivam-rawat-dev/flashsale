package com.enterprise.flashsale.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to reserve flash sale stock")
public class ReservationRequest {

    @NotNull(message = "Product ID cannot be null")
    @Schema(description = "ID of the product to reserve", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Number of units to reserve", example = "1", defaultValue = "1")
    private Integer quantity = 1;
}
