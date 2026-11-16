package com.pvqims.user;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.user.dto.CreateUserRequest;
import com.pvqims.user.dto.UpdateUserRequest;
import com.pvqims.user.dto.UserSummary;
import com.pvqims.user.entity.AppUser;
import com.pvqims.user.mapper.AppUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户管理（FR-02）。角色变更与停用不撤销该用户已有令牌，SRS 未要求。
 */
@Service
public class UserService {

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserSummary> page(String keyword, String role, Boolean isActive, long page, long pageSize) {
        LambdaQueryWrapper<AppUser> query = new LambdaQueryWrapper<AppUser>()
            .and(StringUtils.hasText(keyword), w -> w
                .like(AppUser::getUsername, keyword).or().like(AppUser::getDisplayName, keyword))
            .eq(StringUtils.hasText(role), AppUser::getRole, role)
            .eq(isActive != null, AppUser::getIsActive, isActive)
            .orderByAsc(AppUser::getId);
        Page<AppUser> result = userMapper.selectPage(new Page<>(page, pageSize), query);
        List<UserSummary> records = result.getRecords().stream().map(UserSummary::from).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public UserSummary create(CreateUserRequest request) {
        existsByUsername(request.username());
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        user.setIsActive(true);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发插入同名用户时唯一索引兜底。
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        return UserSummary.from(user);
    }

    public UserSummary update(Long id, UpdateUserRequest request) {
        AppUser user = getExisting(id);
        // 稀疏更新：updateById 只写入非空字段，password_hash 不进 SET 列表，也不会回滚并发的改密。
        AppUser update = new AppUser();
        update.setId(id);
        update.setDisplayName(request.displayName());
        update.setRole(request.role());
        update.setIsActive(request.isActive());
        userMapper.updateById(update);
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        user.setIsActive(request.isActive());
        return UserSummary.from(user);
    }

    public void resetPassword(Long id, String newPassword) {
        getExisting(id);
        // 同上：只写密码字段，不会回滚并发的显示名/角色变更。
        AppUser update = new AppUser();
        update.setId(id);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    private AppUser getExisting(Long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    /**
     * 用户名唯一索引使用 utf8mb4_0900_ai_ci，仅大小写不同也算重复。
     */
    private void existsByUsername(String username) {
        if (userMapper.exists(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username))) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
    }
}
