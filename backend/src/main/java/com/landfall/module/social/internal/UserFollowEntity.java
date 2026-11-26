package com.landfall.module.social.internal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单向关注记录（{@code followerId -> followeeId}）：双向记录实时构成 MutualFollow，不存冗余互关列。
 */
@TableName("user_follows")
class UserFollowEntity {

    @TableId
    private UUID id;

    private UUID followerId;
    private UUID followeeId;
    private Instant createdAt;

    static UserFollowEntity of(UUID followerId, UUID followeeId) {
        UserFollowEntity follow = new UserFollowEntity();
        follow.followerId = Objects.requireNonNull(followerId, "followerId must not be null");
        follow.followeeId = Objects.requireNonNull(followeeId, "followeeId must not be null");
        return follow;
    }

    UUID getFollowerId() {
        return followerId;
    }

    UUID getFolloweeId() {
        return followeeId;
    }
}
