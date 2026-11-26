package com.landfall.common.activity;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 跨模块活跃行为流水记录器，写入默认参与调用方当前数据库事务。
 */
@Service
public class ActivityLogRecorder {

    private final ActivityLogMapper activityLogMapper;

    ActivityLogRecorder(ActivityLogMapper activityLogMapper) {
        this.activityLogMapper = activityLogMapper;
    }

    public UUID record(UUID userId, ActivityAction action, UUID targetId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        ActivityLogEntity entity = new ActivityLogEntity(userId, action, targetId);
        activityLogMapper.insert(entity);
        return entity.getId();
    }
}
