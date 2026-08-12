package com.enterprise.flashsale.repository;

import com.enterprise.flashsale.entity.TransactionalOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<TransactionalOutbox, UUID> {
    List<TransactionalOutbox> findTop50ByProcessedFalseOrderByCreatedAtAsc();
}