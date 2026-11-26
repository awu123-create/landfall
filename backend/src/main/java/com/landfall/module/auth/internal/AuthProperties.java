package com.landfall.module.auth.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "landfall.auth")
record AuthProperties(
        @DefaultValue("30m") Duration permissionCacheTtl, @Valid @DefaultValue BootstrapAdmin bootstrapAdmin) {

    @AssertTrue(message = "landfall.auth.permission-cache-ttl must be positive") boolean isPermissionCacheTtlPositive() {
        return permissionCacheTtl != null && permissionCacheTtl.isPositive();
    }

    record BootstrapAdmin(
            @DefaultValue("false") boolean enabled, String username, String email, String password, String nickname) {

        @AssertTrue(message = "landfall.auth.bootstrap-admin credentials must be valid when enabled") boolean isCredentialsValidWhenEnabled() {
            if (!enabled) {
                return true;
            }
            return hasText(username) && hasText(email) && email.contains("@") && hasText(password) && hasText(nickname);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
