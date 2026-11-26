package com.landfall.module.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * 跨模块只读的用户展示快照：供社交等模块渲染用户卡片，不暴露账号敏感字段。
 */
public record UserSummary(UUID userId, String nickname, String avatarUrl, String bio) {

    public UserSummary {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(nickname, "nickname must not be null");
        Objects.requireNonNull(bio, "bio must not be null");
    }
}
