package com.enterprise.flashsale.controller;

import com.enterprise.flashsale.entity.Inventory;
import com.enterprise.flashsale.repository.InventoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory Management", description = "Endpoints for pre-warming stock and managing inventory")
public class InventoryController {

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;

    public InventoryController(InventoryRepository inventoryRepository, StringRedisTemplate redisTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/warmup")
    @Operation(summary = "Pre-warm inventory", description = "Initializes stock in PostgreSQL database and pre-warms atomic counter in Redis cache.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventory successfully initialized and warmed up"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role")
    })
    public ResponseEntity<Map<String, Object>> warmupInventory(
            @Parameter(description = "Product ID to warm up", required = true, example = "101")
            @RequestParam Long itemId,
            @Parameter(description = "Total initial stock count", required = true, example = "100")
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
