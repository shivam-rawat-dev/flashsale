package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.dto.request.ReservationRequest;
import com.enterprise.flashsale.dto.response.ReservationResponse;
import com.enterprise.flashsale.service.InventoryReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final InventoryReservationService reservationService;

    public ReservationController(InventoryReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestHeader(value = "X-User-Id", defaultValue = "1001") Long userId,
            @Valid @RequestBody ReservationRequest request) {

        ReservationResponse response = reservationService.reserveInventory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}