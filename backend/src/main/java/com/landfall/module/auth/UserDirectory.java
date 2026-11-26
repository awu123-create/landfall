package com.landfall.module.auth;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 供全组业务模块按 id 读取用户展示信息，避免跨模块直接访问 {@code sys_user} 表或 auth 内部实现。
 */
public class UserDirectory {

    private final Function<Collection<UUID>, Map<UUID, UserSummary>> lookup;

    public UserDirectory(Function<Collection<UUID>, Map<UUID, UserSummary>> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup must not be null");
    }

    public Optional<UserSummary> findById(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return Optional.ofNullable(findAllByIds(Set.of(userId)).get(userId));
    }

    /**
     * 批量读取未软删用户；缺失的 id 不出现在结果中，由调用方按主键未命中处理。
     */
    public Map<UUID, UserSummary> findAllByIds(Collection<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(lookup.apply(Set.copyOf(userIds)));
    }
}
