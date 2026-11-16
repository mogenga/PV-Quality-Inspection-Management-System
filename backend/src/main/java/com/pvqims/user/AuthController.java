package com.pvqims.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pvqims.common.api.ApiResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.security.JwtService;
import com.pvqims.security.TokenStore;
import com.pvqims.user.dto.LoginRequest;
import com.pvqims.user.dto.LoginResponse;
import com.pvqims.user.dto.UserSummary;
import com.pvqims.user.entity.AppUser;
import com.pvqims.user.mapper.AppUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与登出。三种登录失败（用户不存在、已停用、密码错误）返回同一错误，避免泄露账号状态。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 用户不存在或已停用时用它执行一次 BCrypt，使三种登录失败的耗时一致，避免通过响应时间枚举账号。
     * 由 {@code BCryptPasswordEncoder}（强度 10）对 {@code "dummy-password-never-used"} 生成，非机密。
     */
    private static final String DUMMY_PASSWORD_HASH =
        "$2a$10$SujDwUkm/J0OUk8IjE1RFOfbZcsEmXqUg2ZYe3d3vtZfp5vMbm1Wy";

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public AuthController(AppUserMapper userMapper, PasswordEncoder passwordEncoder,
                          JwtService jwtService, TokenStore tokenStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getUsername, request.username()));
        boolean canLogIn = user != null && Boolean.TRUE.equals(user.getIsActive());
        boolean passwordMatches = passwordEncoder.matches(
            request.password(), canLogIn ? user.getPasswordHash() : DUMMY_PASSWORD_HASH);
        if (!passwordMatches || !canLogIn) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        JwtService.IssuedToken issued = jwtService.issue(user.getId(), user.getUsername(), user.getRole());
        tokenStore.save(issued.jti(), user.getId(), jwtService.ttl());
        return ApiResult.ok(new LoginResponse(issued.token(), UserSummary.from(user)));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith(BEARER_PREFIX)
            ? authorization.substring(BEARER_PREFIX.length())
            : authorization;
        Claims claims = jwtService.parse(token);
        tokenStore.revoke(claims.getId());
        return ApiResult.ok();
    }
}
