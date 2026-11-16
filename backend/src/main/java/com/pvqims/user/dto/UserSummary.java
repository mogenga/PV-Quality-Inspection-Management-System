package com.pvqims.user.dto;

import java.time.LocalDateTime;

import com.pvqims.user.entity.AppUser;

/**
 * 对外展示的用户信息，绝不包含密码哈希。
 */
public record UserSummary(Long id, String username, String displayName, String role,
                          Boolean isActive, LocalDateTime createdAt) {

    public static UserSummary from(AppUser user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
            user.getIsActive(), user.getCreatedAt());
    }
}
