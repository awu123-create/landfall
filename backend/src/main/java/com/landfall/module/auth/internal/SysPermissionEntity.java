package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("sys_permission")
class SysPermissionEntity {

    @TableId
    private UUID id;

    private UUID parentId;
    private String code;
    private String name;
    private Integer sortOrder;
    private Instant createdAt;

    UUID getId() {
        return id;
    }

    UUID getParentId() {
        return parentId;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    Integer getSortOrder() {
        return sortOrder;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
