package com.pvqims.security;

/**
 * 通过 JWT 认证后放入 SecurityContext 的当前用户。
 */
public record LoginUser(Long id, String username, String role) {
}
