package com.enterprise.flashsale.service;

import com.enterprise.flashsale.entity.Reservation;
import com.enterprise.flashsale.entity.Reservation.ReservationStatus;
import com.enterprise.flashsale.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Background Auto-Release Worker for expired unpurchased inventory reservations.
 * Periodically polls the database for expired reservations, updates status to EXPIRED,
 * and atomically replenishes the held stock back to Redis and Postgres.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final InventoryReservationService reservationService;

    @Scheduled(fixedDelay = 5000)
    public void cleanupExpiredReservations() {
        try {
            Instant now = Instant.now();
            List<Reservation> expiredReservations = reservationRepository.findByStatusAndExpiresAtBefore(
                    ReservationStatus.RESERVED,
                    now,
                    PageRequest.of(0, 50)
            );

            if (expiredReservations.isEmpty()) {
                return;
            }

            log.info("Found {} expired reservations pending stock recovery", expiredReservations.size());

            for (Reservation reservation : expiredReservations) {
                try {
                    reservationService.processTimeout(reservation.getReservationId(), reservation.getItemId());
                } catch (Exception e) {
                    log.error("Failed to release stock for expired reservation {}", reservation.getReservationId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error occurred in ReservationExpiryScheduler", e);
        }
    }
}
