package com.landfall.module.auth.internal;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

final class OpaqueTokenCodec {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private OpaqueTokenCodec() {}

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    static Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (token.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(token);
    }
}
