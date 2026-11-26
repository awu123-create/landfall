package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("sys_role")
class SysRoleEntity {

    @TableId
    private UUID id;

    private String code;
    private String name;
    private String description;

    @TableField("is_builtin")
    private Boolean builtin;

    private Instant createdAt;
    private Instant updatedAt;

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    Boolean getBuiltin() {
        return builtin;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
