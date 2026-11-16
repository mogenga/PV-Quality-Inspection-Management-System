package com.pvqims.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 生产环境防呆：{@code application.yml} 里的默认 JWT 密钥是公开仓库字符串，
 * 若部署时忘记注入 {@code JWT_SECRET}，任何人都能伪造令牌。非 local/test 环境直接拒绝启动。
 */
@Component
public class JwtSecretGuard {

    /** 必须与 application.yml 中 {@code pvqims.jwt.secret} 的兜底值保持一致。 */
    static final String DEFAULT_SECRET = "pvqims-dev-secret-key-must-be-at-least-32-bytes";

    JwtSecretGuard(JwtService.JwtProperties properties, Environment environment) {
        if (DEFAULT_SECRET.equals(properties.secret()) && !environment.matchesProfiles("local | test")) {
            throw new IllegalStateException(
                "JWT_SECRET 未设置：禁止在 local/test 以外的环境使用默认开发密钥，请通过环境变量注入至少 32 字节的随机密钥");
        }
    }
}
