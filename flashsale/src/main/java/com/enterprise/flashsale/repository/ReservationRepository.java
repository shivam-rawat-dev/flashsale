package com.enterprise.flashsale.repository;

import com.enterprise.flashsale.entity.Reservation;
import com.enterprise.flashsale.entity.Reservation.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Optional<Reservation> findByUserIdAndItemIdAndStatus(Long userId, Long itemId, ReservationStatus status);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant expiresAt, Pageable pageable);
}
