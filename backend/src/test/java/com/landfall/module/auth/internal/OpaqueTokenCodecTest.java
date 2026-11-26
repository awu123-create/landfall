package com.landfall.module.auth.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.common.id.UuidV7Generator;
import com.landfall.module.auth.AuthContext;
import com.landfall.module.auth.AuthenticatedUser;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpaqueTokenCodecTest {

    private static final Pattern BASE64_URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    @Test
    void generate_produces32ByteCryptoRandomBase64UrlTokens() {
        String first = OpaqueTokenCodec.generate();
        String second = OpaqueTokenCodec.generate();

        assertTrue(BASE64_URL_PATTERN.matcher(first).matches(), first);
        assertTrue(BASE64_URL_PATTERN.matcher(second).matches(), second);
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
        assertNotEquals(first, second);
    }

    @Test
    void extractBearerToken_returnsTokenForValidHeader() {
        String token = OpaqueTokenCodec.generate();
        assertEquals(Optional.of(token), OpaqueTokenCodec.extractBearerToken("Bearer " + token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Basic abcdef", "Bearer", "Bearer ", "Bearer   ", "Bearer a b", "bearer token"})
    void extractBearerToken_rejectsMissingOrMalformedHeaders(String header) {
        assertTrue(OpaqueTokenCodec.extractBearerToken(header).isEmpty());
    }

    @Test
    void authContext_exposesAuthenticatedAndGuestContractsWithoutSpring() {
        UUID userId = UuidV7Generator.generate();
        AuthenticatedUser principal =
                new AuthenticatedUser(userId, "alice", Set.of("ROLE_ADMIN"), Set.of("admin:access"));

        AuthContext authed = new AuthContext(() -> Optional.of(principal));
        assertEquals(Optional.of(userId), authed.currentUserId());
        assertEquals(userId, authed.requireUserId());
        assertTrue(authed.hasPermission("admin:access"));
        assertFalse(authed.hasPermission("admin:role:manage"));

        AuthContext guest = new AuthContext(Optional::empty);
        assertTrue(guest.currentUserId().isEmpty());
        assertFalse(guest.hasPermission("admin:access"));
        BusinessException ex = assertThrows(BusinessException.class, guest::requireUserId);
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void authProperties_validatesPositiveCacheTtlAndConditionalBootstrapAdmin() {
        AuthProperties.BootstrapAdmin disabledBlank = new AuthProperties.BootstrapAdmin(false, "", "", "", "");
        assertTrue(disabledBlank.isCredentialsValidWhenEnabled());
        assertTrue(new AuthProperties(Duration.ofMinutes(30), disabledBlank).isPermissionCacheTtlPositive());
        assertFalse(new AuthProperties(Duration.ZERO, disabledBlank).isPermissionCacheTtlPositive());

        AuthProperties.BootstrapAdmin enabledMissingPassword =
                new AuthProperties.BootstrapAdmin(true, "admin", "admin@landfall.local", "", "管理员");
        assertFalse(enabledMissingPassword.isCredentialsValidWhenEnabled());
    }
}
