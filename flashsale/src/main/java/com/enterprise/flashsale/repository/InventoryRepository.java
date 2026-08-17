package com.enterprise.flashsale.repository;

import com.enterprise.flashsale.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /**
     * Atomically reserve stock in DB. Enforces availableStock >= quantity.
     * @return Number of rows updated (1 if successful, 0 if insufficient stock)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Inventory i " +
            "SET i.availableStock = i.availableStock - :quantity, " +
            "    i.reservedStock = i.reservedStock + :quantity, " +
            "    i.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE i.productId = :productId " +
            "  AND i.availableStock >= :quantity")
    int reserveStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Confirms purchase after successful payment. Deducts reserved & total stock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Inventory i " +
            "SET i.reservedStock = i.reservedStock - :quantity, " +
            "    i.totalStock = i.totalStock - :quantity, " +
            "    i.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE i.productId = :productId " +
            "  AND i.reservedStock >= :quantity")
    int confirmDeduction(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Compensating action: releases reserved stock back to available stock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Inventory i " +
            "SET i.availableStock = i.availableStock + :quantity, " +
            "    i.reservedStock = i.reservedStock - :quantity, " +
            "    i.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE i.productId = :productId " +
            "  AND i.reservedStock >= :quantity")
    int releaseReservedStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}