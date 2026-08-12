-- KEYS[1]: inventory:{item_id}
-- KEYS[2]: reservation:{user_id}:{item_id}
-- ARGV[1]: requested_quantity
-- ARGV[2]: ttl_seconds
-- ARGV[3]: reservation_id

local current_stock = redis.call('GET', KEYS[1])

if not current_stock then
    return -1
end

if tonumber(current_stock) < tonumber(ARGV[1]) then
    return 0
end

local existing_hold = redis.call('EXISTS', KEYS[2])
if existing_hold == 1 then
    return -2
end

redis.call('DECRBY', KEYS[1], ARGV[1])

local hold_payload = cjson.encode({
    reservationId = ARGV[3],
    quantity = ARGV[1]
})
redis.call('SETEX', KEYS[2], tonumber(ARGV[2]), hold_payload)

return 1