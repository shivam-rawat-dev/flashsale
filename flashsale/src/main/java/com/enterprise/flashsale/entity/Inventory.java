package com.enterprise.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "inventories",
        indexes = {
                @Index(name = "idx_inventory_product_id", columnList = "product_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock;

    /**
     * Optimistic locking version field to prevent race conditions during DB synchronization.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.reservedStock == null) {
            this.reservedStock = 0;
        }
        if (this.availableStock == null) {
            this.availableStock = this.totalStock;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Domain Logic Methods ---

    public boolean hasSufficientStock(int quantity) {
        return this.availableStock != null && this.availableStock >= quantity;
    }

    public void reserveStock(int quantity) {
        if (!hasSufficientStock(quantity)) {
            throw new IllegalStateException("Insufficient stock to reserve");
        }
        this.availableStock -= quantity;
        this.reservedStock += quantity;
    }

    public void releaseReservedStock(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.availableStock += quantity;
    }

    public void confirmDeduction(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.totalStock = Math.max(0, this.totalStock - quantity);
    }
}