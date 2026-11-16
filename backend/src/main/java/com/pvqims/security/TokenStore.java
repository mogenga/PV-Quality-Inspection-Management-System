package com.pvqims.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 有效会话登记表。JWT 本身不可撤销，登出时删除 Redis 中的 jti 使旧令牌立即失效。
 */
@Component
public class TokenStore {

    static final String KEY_PREFIX = "pvqims:token:";

    private final StringRedisTemplate redis;

    public TokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(String jti, Long userId, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + jti, userId.toString(), ttl);
    }

    public boolean isActive(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }

    public void revoke(String jti) {
        redis.delete(KEY_PREFIX + jti);
    }
}
