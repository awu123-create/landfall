package com.landfall.module.social.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface UserFollowMapper extends BaseMapper<UserFollowEntity> {

    /** 单向关注是否存在；双向同时存在即构成 MutualFollow。 */
    default boolean existsFollow(UUID followerId, UUID followeeId) {
        return selectCount(Wrappers.<UserFollowEntity>lambdaQuery()
                        .eq(UserFollowEntity::getFollowerId, followerId)
                        .eq(UserFollowEntity::getFolloweeId, followeeId))
                > 0;
    }

    default long countFollowers(UUID followeeId) {
        return selectCount(Wrappers.<UserFollowEntity>lambdaQuery().eq(UserFollowEntity::getFolloweeId, followeeId));
    }

    default long countFollowing(UUID followerId) {
        return selectCount(Wrappers.<UserFollowEntity>lambdaQuery().eq(UserFollowEntity::getFollowerId, followerId));
    }
}
