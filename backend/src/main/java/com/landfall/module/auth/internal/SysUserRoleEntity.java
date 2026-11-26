package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("sys_user_role")
class SysUserRoleEntity {

    @TableId
    private UUID id;

    private UUID userId;
    private UUID roleId;
    private Instant createdAt;

    SysUserRoleEntity() {}

    SysUserRoleEntity(UUID userId, UUID roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getRoleId() {
        return roleId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
