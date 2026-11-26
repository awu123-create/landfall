package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class BootstrapAdminInitializer implements ApplicationRunner {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AuthProperties authProperties;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final AuthRbacCache authRbacCache;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    BootstrapAdminInitializer(
            AuthProperties authProperties,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            AuthRbacCache authRbacCache,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate) {
        this.authProperties = authProperties;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.authRbacCache = authRbacCache;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin cfg = authProperties.bootstrapAdmin();
        if (!cfg.enabled()) {
            return;
        }

        String username = cfg.username().trim();
        String email = cfg.email().trim().toLowerCase(Locale.ROOT);
        SysUserEntity adminUser = findActiveByUsername(username);
        boolean roleBound = false;

        if (adminUser == null) {
            String passwordHash = passwordEncoder.encode(cfg.password());
            try {
                adminUser = transactionTemplate.execute(status -> {
                    SysUserEntity created = SysUserEntity.newUser(
                            username, email, passwordHash, cfg.nickname().trim());
                    sysUserMapper.insert(created);
                    bindAdminRoleIfMissing(created.getId());
                    return created;
                });
                roleBound = true;
            } catch (DuplicateKeyException ex) {
                adminUser = findActiveByUsername(username);
                if (adminUser == null) {
                    throw ex;
                }
            }
        }

        UUID adminId = adminUser.getId();
        if (!roleBound) {
            roleBound = Boolean.TRUE.equals(transactionTemplate.execute(status -> bindAdminRoleIfMissing(adminId)));
        }
        if (roleBound) {
            authRbacCache.evict(adminId);
        }
    }

    private boolean bindAdminRoleIfMissing(UUID userId) {
        SysRoleEntity adminRole =
                sysRoleMapper.selectOne(Wrappers.<SysRoleEntity>lambdaQuery().eq(SysRoleEntity::getCode, ROLE_ADMIN));
        if (adminRole == null) {
            throw new IllegalStateException("Built-in role ROLE_ADMIN is missing");
        }
        Long boundCount = sysUserRoleMapper.selectCount(Wrappers.<SysUserRoleEntity>lambdaQuery()
                .eq(SysUserRoleEntity::getUserId, userId)
                .eq(SysUserRoleEntity::getRoleId, adminRole.getId()));
        if (boundCount == 0L) {
            sysUserRoleMapper.insert(new SysUserRoleEntity(userId, adminRole.getId()));
            return true;
        }
        return false;
    }

    private SysUserEntity findActiveByUsername(String username) {
        return sysUserMapper.selectActiveByUsernameOrEmail(username, "").stream()
                .filter(row -> row.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }
}
