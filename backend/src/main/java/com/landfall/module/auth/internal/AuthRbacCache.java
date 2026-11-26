package com.landfall.module.auth.internal;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landfall.module.auth.AuthenticatedUser;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class AuthRbacCache implements StpInterface {

    static final String KEY_PREFIX = "landfall:auth:rbac:";

    private final SysUserRoleMapper sysUserRoleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    AuthRbacCache(
            SysUserRoleMapper sysUserRoleMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AuthProperties authProperties) {
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    RbacSnapshot loadForUser(UUID userId) {
        String key = KEY_PREFIX + userId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return deserialize(cached);
        }

        List<SysUserRoleMapper.RolePermissionRow> rows = sysUserRoleMapper.selectRolePermissionsByUserId(userId);
        List<String> roles = rows.stream()
                .map(SysUserRoleMapper.RolePermissionRow::roleCode)
                .distinct()
                .sorted()
                .toList();
        List<String> permissions = rows.stream()
                .map(SysUserRoleMapper.RolePermissionRow::permissionCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        RbacSnapshot loaded = new RbacSnapshot(roles, permissions);
        stringRedisTemplate.opsForValue().set(key, serialize(loaded), authProperties.permissionCacheTtl());
        return loaded;
    }

    void evict(UUID userId) {
        stringRedisTemplate.delete(KEY_PREFIX + userId);
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        UUID userId = UUID.fromString(loginId.toString());
        Optional<AuthenticatedUser> requestUser = AuthConfig.currentUser();
        if (requestUser.isPresent() && userId.equals(requestUser.get().userId())) {
            return List.copyOf(requestUser.get().permissionCodes());
        }
        return loadForUser(userId).permissions();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        UUID userId = UUID.fromString(loginId.toString());
        Optional<AuthenticatedUser> requestUser = AuthConfig.currentUser();
        if (requestUser.isPresent() && userId.equals(requestUser.get().userId())) {
            return List.copyOf(requestUser.get().roleCodes());
        }
        return loadForUser(userId).roles();
    }

    private String serialize(RbacSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize RBAC snapshot", ex);
        }
    }

    private RbacSnapshot deserialize(String raw) {
        try {
            return objectMapper.readValue(raw, RbacSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize RBAC snapshot", ex);
        }
    }

    record RbacSnapshot(List<String> roles, List<String> permissions) {}
}
