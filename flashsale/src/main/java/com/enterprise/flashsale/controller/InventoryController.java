package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.entity.Inventory;
import com.enterprise.flashsale.repository.InventoryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;

    public InventoryController(InventoryRepository inventoryRepository, StringRedisTemplate redisTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> warmupInventory(
            @RequestParam Long itemId,
            @RequestParam int totalStock) {

        // 1. Save or update inventory in PostgreSQL matching the entity fields
        Optional<Inventory> existing = inventoryRepository.findByProductId(itemId);

        Inventory inventory;
        if (existing.isPresent()) {
            inventory = existing.get();
            inventory.setTotalStock(totalStock);
            inventory.setAvailableStock(totalStock);
            inventory.setReservedStock(0);
        } else {
            inventory = Inventory.builder()
                    .productId(itemId)
                    .totalStock(totalStock)
                    .availableStock(totalStock)
                    .reservedStock(0)
                    .build();
        }

        inventoryRepository.save(inventory);

        // 2. Pre-warm Redis atomic inventory counter
        String redisKey = "inventory:" + itemId;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(totalStock));

        return ResponseEntity.ok(Map.of(
                "message", "Inventory successfully warmed up in PostgreSQL and Redis",
                "itemId", itemId,
                "totalStock", totalStock,
                "redisKey", redisKey
        ));
    }
}