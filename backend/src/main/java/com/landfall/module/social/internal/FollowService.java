package com.landfall.module.social.internal;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.module.auth.UserDirectory;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 关注/取关写入：单向记录幂等写入，互关状态实时由双向记录判定。
 */
@Service
class FollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserDirectory userDirectory;
    private final TransactionTemplate transactionTemplate;

    FollowService(
            UserFollowMapper userFollowMapper, UserDirectory userDirectory, TransactionTemplate transactionTemplate) {
        this.userFollowMapper = userFollowMapper;
        this.userDirectory = userDirectory;
        this.transactionTemplate = transactionTemplate;
    }

    FollowStateResp follow(UUID viewerId, UUID targetUserId) {
        requireFollowTarget(viewerId, targetUserId);
        insertIgnoringDuplicate(viewerId, targetUserId);
        return currentState(viewerId, targetUserId);
    }

    FollowStateResp unfollow(UUID viewerId, UUID targetUserId) {
        requireActiveUser(targetUserId);
        transactionTemplate.executeWithoutResult(
                status -> userFollowMapper.delete(Wrappers.<UserFollowEntity>lambdaQuery()
                        .eq(UserFollowEntity::getFollowerId, viewerId)
                        .eq(UserFollowEntity::getFolloweeId, targetUserId)));
        return currentState(viewerId, targetUserId);
    }

    private void insertIgnoringDuplicate(UUID viewerId, UUID targetUserId) {
        try {
            transactionTemplate.executeWithoutResult(
                    status -> userFollowMapper.insert(UserFollowEntity.of(viewerId, targetUserId)));
        } catch (DuplicateKeyException ex) {
            // 唯一约束 uk_user_follows_pair 拦截并发重复关注：已存在关注关系与本次写入等价，按幂等成功处理。
            // 事务已在该次写入失败时回滚，后续读取在新的数据库会话中进行，不受 PostgreSQL aborted 事务影响。
        }
    }

    private FollowStateResp currentState(UUID viewerId, UUID targetUserId) {
        boolean following = userFollowMapper.existsFollow(viewerId, targetUserId);
        boolean mutualFollow = following && userFollowMapper.existsFollow(targetUserId, viewerId);
        return new FollowStateResp(following, mutualFollow, userFollowMapper.countFollowers(targetUserId));
    }

    private void requireFollowTarget(UUID viewerId, UUID targetUserId) {
        if (viewerId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        requireActiveUser(targetUserId);
    }

    private void requireActiveUser(UUID userId) {
        userDirectory.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
