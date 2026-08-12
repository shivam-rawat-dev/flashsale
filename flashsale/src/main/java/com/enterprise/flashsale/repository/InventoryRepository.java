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
     * Atomically reserve stock during checkout.
     * Prevents overselling by enforcing (availableStock >= quantity) in the WHERE clause.
     *
     * @return 1 if successful, 0 if insufficient stock or product not found
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i " +
            "SET i.availableStock = i.availableStock - :quantity, " +
            "    i.reservedStock = i.reservedStock + :quantity, " +
            "    i.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE i.productId = :productId " +
            "  AND i.availableStock >= :quantity")
    int reserveStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Confirms the purchase after successful payment.
     * Deducts from reserved stock and total stock.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i " +
            "SET i.reservedStock = i.reservedStock - :quantity, " +
            "    i.totalStock = i.totalStock - :quantity, " +
            "    i.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE i.productId = :productId " +
            "  AND i.reservedStock >= :quantity")
    int confirmDeduction(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Releases reserved stock back to available stock (e.g., payment failure, order timeout).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i " +
            "SET i.availableStock = i.availableStock + :quantity, " +
            "    i.reservedStock = i.reservedStock - :quantity, " +
            "    i.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE i.productId = :productId " +
            "  AND i.reservedStock >= :quantity")
    int releaseReservedStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}