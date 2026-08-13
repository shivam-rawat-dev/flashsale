package com.enterprise.flashsale.repository;

import com.enterprise.flashsale.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Optional<Reservation> findByUserIdAndItemIdAndStatus(Long userId, Long itemId, String status);
}