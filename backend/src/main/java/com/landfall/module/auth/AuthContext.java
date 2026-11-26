package com.landfall.module.auth;

import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 供全组业务模块注入读取当前请求认证上下文的公开入口。
 */
public class AuthContext {

    private final Supplier<Optional<AuthenticatedUser>> currentUserSupplier;

    public AuthContext(Supplier<Optional<AuthenticatedUser>> currentUserSupplier) {
        this.currentUserSupplier = Objects.requireNonNull(currentUserSupplier, "currentUserSupplier must not be null");
    }

    public Optional<AuthenticatedUser> currentUser() {
        return currentUserSupplier.get();
    }

    public Optional<UUID> currentUserId() {
        return currentUser().map(AuthenticatedUser::userId);
    }

    public UUID requireUserId() {
        return currentUserId().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public boolean hasPermission(String permissionCode) {
        return currentUser().map(user -> user.hasPermission(permissionCode)).orElse(false);
    }
}
