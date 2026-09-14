package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.dto.request.ReservationRequest;
import com.enterprise.flashsale.dto.response.ReservationResponse;
import com.enterprise.flashsale.entity.AppUser;
import com.enterprise.flashsale.repository.UserRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Endpoints for reserving flash sale stock with distributed locking")
public class ReservationController {

    private final InventoryReservationService reservationService;
    private final UserRepository userRepository;

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
            Authentication authentication,
            @Parameter(description = "User ID placing the reservation", required = false, example = "1001")
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @Valid @RequestBody ReservationRequest request) {

        Long effectiveUserId = null;
        if (request.getUserId() != null) {
            effectiveUserId = request.getUserId();
        } else if (headerUserId != null) {
            effectiveUserId = headerUserId;
        } else if (authentication != null && authentication.getName() != null) {
            effectiveUserId = userRepository.findByEmail(authentication.getName())
                    .map(AppUser::getId)
                    .orElse(null);
        }

        if (effectiveUserId == null) {
            effectiveUserId = 1001L;
        }

        ReservationResponse response = reservationService.reserveStock(effectiveUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
