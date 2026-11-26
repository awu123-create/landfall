package com.landfall.module.auth.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.landfall.common.activity.ActivityAction;
import com.landfall.common.activity.ActivityLogRecorder;
import com.landfall.common.id.UuidV7Generator;
import com.landfall.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class RbacBaselineIT extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final ActivityLogRecorder activityLogRecorder;

    @Autowired
    RbacBaselineIT(
            JdbcTemplate jdbcTemplate,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysPermissionMapper sysPermissionMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysRolePermissionMapper sysRolePermissionMapper,
            ActivityLogRecorder activityLogRecorder) {
        this.jdbcTemplate = jdbcTemplate;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.activityLogRecorder = activityLogRecorder;
    }

    @Test
    void flywayMigration_createsSixBaselineTablesBuiltInRolesAndPermissionTree() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertTrue(tables.containsAll(List.of(
                "sys_user",
                "sys_role",
                "sys_permission",
                "sys_user_role",
                "sys_role_permission",
                "user_activity_logs")));

        List<SysRoleEntity> roles = sysRoleMapper.selectList(null);
        Map<String, SysRoleEntity> rolesByCode =
                roles.stream().collect(Collectors.toMap(SysRoleEntity::getCode, role -> role));
        assertEquals(Set.of("ROLE_USER", "ROLE_MODERATOR", "ROLE_ADMIN"), rolesByCode.keySet());
        roles.forEach(role -> assertEquals(Boolean.TRUE, role.getBuiltin()));

        List<SysPermissionEntity> permissions = sysPermissionMapper.selectList(
                Wrappers.<SysPermissionEntity>lambdaQuery().orderByAsc(SysPermissionEntity::getSortOrder));
        assertEquals(9, permissions.size());
        SysPermissionEntity root = permissions.getFirst();
        assertEquals("admin:access", root.getCode());
        assertNull(root.getParentId());
        for (int i = 1; i < permissions.size(); i++) {
            assertEquals(root.getId(), permissions.get(i).getParentId());
        }

        Map<UUID, String> permissionCodeById = permissions.stream()
                .collect(Collectors.toMap(SysPermissionEntity::getId, SysPermissionEntity::getCode));
        assertEquals(
                9,
                permissionCodesForRole(rolesByCode.get("ROLE_ADMIN").getId(), permissionCodeById)
                        .size());
        assertEquals(
                Set.of(
                        "admin:access",
                        "admin:post:manage",
                        "admin:comment:manage",
                        "admin:tag:manage",
                        "admin:audit:review",
                        "admin:word:manage",
                        "admin:stat:view"),
                permissionCodesForRole(rolesByCode.get("ROLE_MODERATOR").getId(), permissionCodeById));
        assertTrue(permissionCodesForRole(rolesByCode.get("ROLE_USER").getId(), permissionCodeById)
                .isEmpty());
    }

    @Test
    @Transactional
    void uuidV7AutoAssignmentAndTimestamps_workAcrossUsersAndJoinTables() {
        SysUserEntity autoIdUser = newUser("auto_user", "auto@landfall.dev", "自动用户");
        sysUserMapper.insert(autoIdUser);
        assertNotNull(autoIdUser.getId());
        assertEquals(7, autoIdUser.getId().version());
        assertEquals(2, autoIdUser.getId().variant());

        SysUserEntity reloaded = sysUserMapper.selectById(autoIdUser.getId());
        assertNotNull(reloaded);
        assertEquals(UserStatus.NORMAL, reloaded.getStatus());
        assertEquals("", reloaded.getBio());
        assertNull(reloaded.getAvatarUrl());
        assertNull(reloaded.getDeletedAt());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());

        UUID explicitId = UuidV7Generator.generate();
        SysUserEntity explicitUser = newUser("explicit_user", "explicit@landfall.dev", "显式用户");
        explicitUser.setId(explicitId);
        sysUserMapper.insert(explicitUser);
        assertEquals(explicitId, explicitUser.getId());
        assertNotNull(sysUserMapper.selectById(explicitId));

        SysRoleEntity userRole =
                sysRoleMapper.selectOne(Wrappers.<SysRoleEntity>lambdaQuery().eq(SysRoleEntity::getCode, "ROLE_USER"));
        SysUserRoleEntity link = new SysUserRoleEntity(autoIdUser.getId(), userRole.getId());
        sysUserRoleMapper.insert(link);
        assertNotNull(link.getId());
        assertEquals(7, link.getId().version());
        assertNotNull(sysUserRoleMapper.selectById(link.getId()).getCreatedAt());
    }

    @Test
    void userPartialUniqueIndexes_enforceCaseInsensitiveActiveUniquenessAndAllowSoftDeletedReuse() {
        SysUserEntity active = newUser("Alice", "Alice@landfall.dev", "爱丽丝");
        SysUserEntity reregistered = newUser("alice", "alice@landfall.dev", "重注册爱丽丝");
        try {
            sysUserMapper.insert(active);

            assertThrows(
                    DuplicateKeyException.class,
                    () -> sysUserMapper.insert(newUser("alice", "other@landfall.dev", "重名用户")));
            assertThrows(
                    DuplicateKeyException.class,
                    () -> sysUserMapper.insert(newUser("other_user", "ALICE@LANDFALL.DEV", "重邮用户")));

            active.setDeletedAt(Instant.now());
            sysUserMapper.updateById(active);

            // 验证禁用全局 @TableLogic：按主键仍能直接读出已软删记录。
            SysUserEntity softDeletedRow = sysUserMapper.selectById(active.getId());
            assertNotNull(softDeletedRow);
            assertNotNull(softDeletedRow.getDeletedAt());

            sysUserMapper.insert(reregistered);
            assertNotNull(reregistered.getId());
            assertEquals(
                    1L,
                    sysUserMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                            .eq(SysUserEntity::getId, reregistered.getId())
                            .isNull(SysUserEntity::getDeletedAt)));
        } finally {
            if (reregistered.getId() != null) {
                sysUserMapper.deleteById(reregistered.getId());
            }
            if (active.getId() != null) {
                sysUserMapper.deleteById(active.getId());
            }
        }
    }

    @Test
    @Transactional
    void activityLogRecorder_persistsLoginAndTargetedEventsWithUuidV7() {
        SysUserEntity user = newUser("logger_user", "logger@landfall.dev", "日志用户");
        sysUserMapper.insert(user);

        UUID loginLogId = activityLogRecorder.record(user.getId(), ActivityAction.LOGIN, null);
        UUID targetPostId = UuidV7Generator.generate();
        UUID postLogId = activityLogRecorder.record(user.getId(), ActivityAction.POST_CREATE, targetPostId);

        assertEquals(7, loginLogId.version());
        assertEquals(7, postLogId.version());

        Map<String, Object> loginRow = jdbcTemplate.queryForMap(
                "SELECT user_id, action_type, target_id, created_at FROM user_activity_logs WHERE id = ?", loginLogId);
        assertEquals(user.getId(), loginRow.get("user_id"));
        assertEquals("LOGIN", loginRow.get("action_type"));
        assertNull(loginRow.get("target_id"));
        assertNotNull(loginRow.get("created_at"));

        Map<String, Object> postRow = jdbcTemplate.queryForMap(
                "SELECT user_id, action_type, target_id FROM user_activity_logs WHERE id = ?", postLogId);
        assertEquals(user.getId(), postRow.get("user_id"));
        assertEquals("POST_CREATE", postRow.get("action_type"));
        assertEquals(targetPostId, postRow.get("target_id"));
    }

    private Set<String> permissionCodesForRole(UUID roleId, Map<UUID, String> permissionCodeById) {
        return sysRolePermissionMapper
                .selectList(
                        Wrappers.<SysRolePermissionEntity>lambdaQuery().eq(SysRolePermissionEntity::getRoleId, roleId))
                .stream()
                .map(link -> permissionCodeById.get(link.getPermissionId()))
                .collect(Collectors.toSet());
    }

    private static SysUserEntity newUser(String username, String email, String nickname) {
        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$placeholderbcryptvalueforintegrationtest0000000000000");
        user.setNickname(nickname);
        return user;
    }
}
