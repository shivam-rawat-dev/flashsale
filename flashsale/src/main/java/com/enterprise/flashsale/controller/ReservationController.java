package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.dto.request.ReservationRequest;
import com.enterprise.flashsale.dto.response.ReservationResponse;
import com.enterprise.flashsale.service.InventoryReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Endpoints for reserving flash sale stock with distributed locking")
public class ReservationController {

    private final InventoryReservationService reservationService;

    @PostMapping
    @Operation(summary = "Reserve item inventory", description = "Executes an atomic Lua script over Redis to temporarily hold stock for a user with TTL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stock successfully reserved",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Sold out or duplicate reservation attempt"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires USER or ADMIN role")
    })
    public ResponseEntity<ReservationResponse> createReservation(
            @Parameter(description = "User ID placing the reservation", required = true, example = "1001")
            @RequestHeader(value = "X-User-Id") Long userId,
            @Valid @RequestBody ReservationRequest request) {

        ReservationResponse response = reservationService.reserveStock(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
