package com.landfall.module.auth.internal;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.landfall.common.activity.ActivityAction;
import com.landfall.common.activity.ActivityLogRecorder;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.module.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AuthService {

    private static final String ROLE_USER = "ROLE_USER";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthRbacCache authRbacCache;
    private final ActivityLogRecorder activityLogRecorder;
    private final TransactionTemplate transactionTemplate;

    AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordEncoder passwordEncoder,
            AuthRbacCache authRbacCache,
            ActivityLogRecorder activityLogRecorder,
            TransactionTemplate transactionTemplate) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.authRbacCache = authRbacCache;
        this.activityLogRecorder = activityLogRecorder;
        this.transactionTemplate = transactionTemplate;
    }

    AuthUserResp register(RegisterReq req) {
        String username = req.username();
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String nickname = (req.nickname() == null || req.nickname().isBlank())
                ? username
                : req.nickname().trim();

        checkActiveConflicts(username, email);

        String passwordHash = passwordEncoder.encode(req.password());
        SysUserEntity created = transactionTemplate.execute(status -> {
            SysUserEntity user = SysUserEntity.newUser(username, email, passwordHash, nickname);
            insertUserCheckingConflict(user);

            SysRoleEntity defaultRole = sysRoleMapper.selectOne(
                    Wrappers.<SysRoleEntity>lambdaQuery().eq(SysRoleEntity::getCode, ROLE_USER));
            if (defaultRole == null) {
                throw new IllegalStateException("Built-in role ROLE_USER is missing");
            }
            sysUserRoleMapper.insert(new SysUserRoleEntity(user.getId(), defaultRole.getId()));
            return user;
        });

        return AuthUserResp.from(created, List.of(ROLE_USER), List.of());
    }

    LoginResp login(LoginReq req) {
        String account = req.account().trim();
        List<SysUserEntity> matches = sysUserMapper.selectActiveByUsernameOrEmail(account, account);
        SysUserEntity user = matches.isEmpty() ? null : matches.getFirst();
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }

        String token = StpUtil.createLoginSession(user.getId().toString());
        AuthRbacCache.RbacSnapshot rbac = authRbacCache.loadForUser(user.getId());
        activityLogRecorder.record(user.getId(), ActivityAction.LOGIN, null);
        return new LoginResp(token, AuthUserResp.from(user, rbac.roles(), rbac.permissions()));
    }

    void logout(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    void changePassword(UUID userId, ChangePasswordReq req) {
        SysUserEntity user = sysUserMapper.selectActiveById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_OLD_PASSWORD);
        }

        String newHash = passwordEncoder.encode(req.newPassword());
        int updated = sysUserMapper.update(
                null,
                Wrappers.<SysUserEntity>lambdaUpdate()
                        .eq(SysUserEntity::getId, userId)
                        .isNull(SysUserEntity::getDeletedAt)
                        .set(SysUserEntity::getPasswordHash, newHash)
                        .set(SysUserEntity::getUpdatedAt, Instant.now()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        StpUtil.logout(userId.toString());
        authRbacCache.evict(userId);
    }

    AuthUserResp currentUserProfile(AuthenticatedUser currentUser) {
        SysUserEntity user = sysUserMapper.selectActiveById(currentUser.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        List<String> roles = currentUser.roleCodes().stream().sorted().toList();
        List<String> permissions =
                currentUser.permissionCodes().stream().sorted().toList();
        return AuthUserResp.from(user, roles, permissions);
    }

    private void checkActiveConflicts(String username, String email) {
        List<SysUserEntity> existing = sysUserMapper.selectActiveByUsernameOrEmail(username, email);
        for (SysUserEntity row : existing) {
            if (row.getUsername().equalsIgnoreCase(username)) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
        }
        if (!existing.isEmpty()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void insertUserCheckingConflict(SysUserEntity user) {
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("uk_sys_user_username_active")) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
            if (message != null && message.contains("uk_sys_user_email_active")) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw ex;
        }
    }
}
