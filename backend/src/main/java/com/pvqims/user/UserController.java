package com.pvqims.user;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.user.dto.CreateUserRequest;
import com.pvqims.user.dto.ResetPasswordRequest;
import com.pvqims.user.dto.UpdateUserRequest;
import com.pvqims.user.dto.UserSummary;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，仅管理员可访问（SRS 4.8、BR-07）。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PageResult<UserSummary>> page(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String role,
                                                   @RequestParam(required = false) Boolean isActive,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(userService.page(keyword, role, isActive, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResult.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserSummary> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResult.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ApiResult.ok();
    }
}
