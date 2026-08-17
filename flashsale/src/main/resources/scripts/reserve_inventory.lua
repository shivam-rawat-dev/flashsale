-- KEYS[1]: inventory:{productId}
-- KEYS[2]: reservation:user:{userId}:{productId}
-- KEYS[3]: flashsale:reservation:{reservationId}
-- ARGV[1]: requested_quantity
-- ARGV[2]: ttl_seconds
-- ARGV[3]: user_id
-- ARGV[4]: product_id

local inventoryKey = KEYS[1]
local userHoldKey = KEYS[2]
local resLookupKey = KEYS[3]

local requestedQty = tonumber(ARGV[1]) or 1
local ttl = tonumber(ARGV[2]) or 600
local userId = ARGV[3]
local productId = ARGV[4]

local currentStock = redis.call('GET', inventoryKey)

if not currentStock then
    return -1 -- Cache not warm
end

if tonumber(currentStock) < requestedQty then
    return 0 -- Insufficient stock
end

if redis.call('EXISTS', userHoldKey) == 1 then
    return -2 -- Duplicate reservation by user
end

-- Deduct stock
redis.call('DECRBY', inventoryKey, requestedQty)

-- Store user lock
redis.call('SETEX', userHoldKey, ttl, "LOCKED")

-- Store lookup key for checkout validation: "userId:productId:quantity"
local payload = string.format("%s:%s:%d", userId, productId, requestedQty)
redis.call('SETEX', resLookupKey, ttl, payload)

return 1 -- Success