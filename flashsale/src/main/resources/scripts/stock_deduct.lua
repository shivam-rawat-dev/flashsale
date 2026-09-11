-- Atomic Flash Sale Stock Reservation Lua Script
-- KEYS[1]: inventory:{productId}
-- KEYS[2]: reservation:{userId}:{productId}
-- KEYS[3]: flashsale:reservation:{reservationId}
-- ARGV[1]: requested_quantity
-- ARGV[2]: ttl_seconds (e.g., 600)
-- ARGV[3]: reservation_id
-- ARGV[4]: user_id
-- ARGV[5]: product_id

local inventoryKey = KEYS[1]
local userReservationKey = KEYS[2]
local orderLookupKey = KEYS[3]

local requestedQuantity = tonumber(ARGV[1]) or 1
local ttlSeconds = tonumber(ARGV[2]) or 600
local reservationId = ARGV[3] or "RESERVED"
local userId = ARGV[4] or ""
local productId = ARGV[5] or ""

-- 1. Check if inventory key exists in Redis
local currentStock = redis.call('GET', inventoryKey)
if not currentStock then
    return -1 -- Inventory not initialized
end

-- 2. Check for duplicate reservation by the same user
if redis.call('EXISTS', userReservationKey) == 1 then
    return -2 -- User already holds an active reservation
end

-- 3. Check if sufficient stock is available
local stockNum = tonumber(currentStock)
if stockNum < requestedQuantity then
    return 0 -- Sold out / insufficient stock
end

-- 4. Atomically decrement stock
redis.call('DECRBY', inventoryKey, requestedQuantity)

-- 5. Atomically set user reservation hold with TTL
redis.call('SETEX', userReservationKey, ttlSeconds, reservationId)

-- 6. Atomically set order lookup key formatted as "userId:productId:quantity" for checkout validation
if orderLookupKey and orderLookupKey ~= "" then
    local payload = string.format("%s:%s:%d", tostring(userId), tostring(productId), requestedQuantity)
    redis.call('SETEX', orderLookupKey, ttlSeconds, payload)
end

-- 7. Return success
return 1
