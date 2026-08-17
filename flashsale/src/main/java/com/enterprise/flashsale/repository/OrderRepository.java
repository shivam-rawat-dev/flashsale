package com.enterprise.flashsale.repository;

import com.enterprise.flashsale.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByReservationId(String reservationId);
    boolean existsByReservationId(String reservationId);
}