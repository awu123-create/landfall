package com.landfall.common.activity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.landfall.common.id.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;

@TableName("user_activity_logs")
class ActivityLogEntity {

    @TableId
    private UUID id;

    private UUID userId;
    private ActivityAction actionType;
    private UUID targetId;
    private Instant createdAt;

    ActivityLogEntity() {}

    ActivityLogEntity(UUID userId, ActivityAction actionType, UUID targetId) {
        this.id = UuidV7Generator.generate();
        this.userId = userId;
        this.actionType = actionType;
        this.targetId = targetId;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    ActivityAction getActionType() {
        return actionType;
    }

    UUID getTargetId() {
        return targetId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
