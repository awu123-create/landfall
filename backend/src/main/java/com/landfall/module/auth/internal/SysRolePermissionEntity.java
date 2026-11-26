package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("sys_role_permission")
class SysRolePermissionEntity {

    @TableId
    private UUID id;

    private UUID roleId;
    private UUID permissionId;
    private Instant createdAt;

    SysRolePermissionEntity() {}

    SysRolePermissionEntity(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    UUID getId() {
        return id;
    }

    UUID getRoleId() {
        return roleId;
    }

    UUID getPermissionId() {
        return permissionId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
