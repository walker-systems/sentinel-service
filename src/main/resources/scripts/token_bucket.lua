local key = KEYS[1]                     -- ID of bucket
local rate = tonumber(ARGV[1])          -- Tokens refilled per second
local capacity = tonumber(ARGV[2])      -- Max tokens per bucket
local now = tonumber(ARGV[3])           -- Time in seconds (Unix epoch)
local requested = tonumber(ARGV[4])     -- Requested tokens (usually 1)

local info = redis.call("HMGET", key, "tokens", "last_refill")
local tokens = tonumber(info[1])
local last_refill = tonumber(info[2])

if tokens == nil then
    token = capacity
    last_refill = now
end

local delta = math.max(0, now - last_refill)
local filled_tokens = math.min(capacity, tokens + (delta * rate))

local allowed = false
local remaining = filled_tokens

if filled_tokens >= requested then
    allowed = true
    remaining = filled_tokens - requested
    redis.call("HMSET", key, "tokens", remaining, "last_refill", now)
    redis.call("EXPIRE", key, 3600)
else
    allowed = false
end

return { allowed and 1 or 0, remaining }
