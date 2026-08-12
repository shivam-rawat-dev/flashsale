package com.enterprise.flashsale.service;

import com.enterprise.flashsale.entity.Inventory;
import com.enterprise.flashsale.repository.InventoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final String STOCK_KEY_PREFIX = "flashsale:stock:";

    private final StringRedisTemplate redisTemplate;
    private final InventoryRepository inventoryRepository;

    private RedisScript<Long> reserveStockScript;

    @PostConstruct
    public void init() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Maps to src/main/resources/scripts/reserve_inventory.lua
        script.setLocation(new ClassPathResource("scripts/reserve_inventory.lua"));
        script.setResultType(Long.class);
        this.reserveStockScript = script;
    }

    /**
     * Warms up Redis cache before the flash sale starts.
     */
    public void preloadStock(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found for product: " + productId));

        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(inventory.getAvailableStock()));
        log.info("Loaded stock for product {} into Redis: {}", productId, inventory.getAvailableStock());
    }

    /**
     * Executes fast pre-deduction in Redis via Lua Script.
     *
     * Script returns:
     *  1  -> Success (stock reserved)
     *  0  -> Insufficient stock (sold out)
     * -1  -> Key does not exist in Redis (uncached)
     */
    public boolean preDeductStock(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;

        Long result = redisTemplate.execute(
                reserveStockScript,
                Collections.singletonList(key),
                String.valueOf(quantity)
        );

        if (result == null) {
            log.error("Lua script execution returned null for product {}", productId);
            return false;
        }

        if (result == 1L) {
            return true;
        } else if (result == 0L) {
            log.warn("Flash sale sold out in Redis for product {}", productId);
            return false;
        } else if (result == -1L) {
            log.warn("Redis stock key missing for product {}. Attempting fallback load.", productId);
            preloadStock(productId);
            // Retry once after warming cache
            Long retryResult = redisTemplate.execute(
                    reserveStockScript,
                    Collections.singletonList(key),
                    String.valueOf(quantity)
            );
            return retryResult != null && retryResult == 1L;
        }

        return false;
    }

    /**
     * Rolls back stock in Redis if downstream order creation or payment setup fails.
     */
    public void rollbackRedisStock(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(key, quantity);
        log.info("Rolled back {} items to Redis stock for product {}", quantity, productId);
    }

    /**
     * Asynchronously or synchronously syncs DB after successful Redis reservation.
     */
    @Transactional
    public boolean syncReservationToDatabase(Long productId, Integer quantity) {
        int updatedRows = inventoryRepository.reserveStock(productId, quantity);
        if (updatedRows == 0) {
            log.error("DB update failed during stock reservation for product {}. Rolling back Redis...", productId);
            rollbackRedisStock(productId, quantity);
            return false;
        }
        return true;
    }
}