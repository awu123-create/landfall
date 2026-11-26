package com.landfall.module.auth;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 跨模块只读当前登录用户快照。
 */
public record AuthenticatedUser(UUID userId, String username, Set<String> roleCodes, Set<String> permissionCodes) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        roleCodes = Set.copyOf(Objects.requireNonNull(roleCodes, "roleCodes must not be null"));
        permissionCodes = Set.copyOf(Objects.requireNonNull(permissionCodes, "permissionCodes must not be null"));
    }

    public boolean hasPermission(String permissionCode) {
        return permissionCode != null && permissionCodes.contains(permissionCode);
    }
}
