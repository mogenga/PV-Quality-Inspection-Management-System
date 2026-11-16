package com.pvqims.security;

import java.io.IOException;
import java.util.List;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从 {@code Authorization: Bearer <token>} 中恢复登录态。
 * 令牌非法或已不在 Redis 会话表中时不抛异常、不设置认证，交由入口点返回 401。
 * 不作为 Bean 暴露：由 {@link SecurityConfig} 直接构造并挂到安全过滤链，避免被 Servlet 容器重复注册。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public JwtAuthenticationFilter(JwtService jwtService, TokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token);
            if (!tokenStore.isActive(claims.getId())) {
                return;
            }
            LoginUser loginUser = new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
            var authentication = new UsernamePasswordAuthenticationToken(
                loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            // 令牌非法或已失效：清空上下文，交由入口点返回 401。
            SecurityContextHolder.clearContext();
        }
    }
}
