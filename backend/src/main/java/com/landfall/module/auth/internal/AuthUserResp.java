package com.landfall.module.auth.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

record AuthUserResp(
        @Schema(description = "用户主键 UUID", requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED) String username,
        @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "昵称", requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
        @Schema(description = "头像地址") String avatarUrl,
        @Schema(description = "个人简介", requiredMode = Schema.RequiredMode.REQUIRED) String bio,
        @Schema(description = "账号状态", requiredMode = Schema.RequiredMode.REQUIRED) UserStatus status,
        @Schema(description = "角色编码列表", requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
        @Schema(description = "PermissionCode 权限集合", requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> permissions) {

    static AuthUserResp from(SysUserEntity user, List<String> roles, List<String> permissions) {
        return new AuthUserResp(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getStatus(),
                List.copyOf(roles),
                List.copyOf(permissions));
    }
}
