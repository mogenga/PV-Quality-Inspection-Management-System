package com.pvqims.security;

import java.time.Duration;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 有效会话登记表。JWT 本身不可撤销，登出时删除 Redis 中的 jti 使旧令牌立即失效。
 * 每个用户另有一个 jti 集合，停用账号、改角色或重置密码时据此一次性踢掉该用户全部会话。
 */
@Component
public class TokenStore {

    static final String KEY_PREFIX = "pvqims:token:";
    static final String USER_KEY_PREFIX = "pvqims:user:";

    private final StringRedisTemplate redis;

    public TokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(String jti, Long userId, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + jti, userId.toString(), ttl);
        redis.opsForSet().add(USER_KEY_PREFIX + userId, jti);
        redis.expire(USER_KEY_PREFIX + userId, ttl);
    }

    public boolean isActive(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }

    public void revoke(String jti) {
        String userId = redis.opsForValue().get(KEY_PREFIX + jti);
        redis.delete(KEY_PREFIX + jti);
        // 尽力而为：用户集合里残留的 jti 会在 revokeAllForUser 时被一并清掉。
        if (userId != null) {
            redis.opsForSet().remove(USER_KEY_PREFIX + userId, jti);
        }
    }

    /**
     * 撤销该用户当前所有有效令牌，用于停用、改角色与重置密码。
     */
    public void revokeAllForUser(Long userId) {
        String userKey = USER_KEY_PREFIX + userId;
        Set<String> jtis = redis.opsForSet().members(userKey);
        if (jtis != null && !jtis.isEmpty()) {
            redis.delete(jtis.stream().map(jti -> KEY_PREFIX + jti).toList());
        }
        redis.delete(userKey);
    }
}
