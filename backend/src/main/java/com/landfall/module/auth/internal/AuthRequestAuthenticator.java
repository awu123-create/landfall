package com.landfall.module.auth.internal;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.module.auth.AuthenticatedUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
class AuthRequestAuthenticator {

    private final SysUserMapper sysUserMapper;
    private final AuthRbacCache authRbacCache;
    private final SaTokenConfig saTokenConfig;

    AuthRequestAuthenticator(SysUserMapper sysUserMapper, AuthRbacCache authRbacCache, SaTokenConfig saTokenConfig) {
        this.sysUserMapper = sysUserMapper;
        this.authRbacCache = authRbacCache;
        this.saTokenConfig = saTokenConfig;
    }

    Optional<AuthenticatedSession> resolveOptional() {
        return Optional.ofNullable(resolve(true).session());
    }

    AuthenticatedSession requireActive(boolean renewSession) {
        TokenResolution resolution = resolve(renewSession);
        if (resolution.session() == null) {
            throw new BusinessException(resolution.failureCode());
        }
        return resolution.session();
    }

    private TokenResolution resolve(boolean renewSession) {
        String rawHeader = SaHolder.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (rawHeader == null) {
            return TokenResolution.failed(ErrorCode.UNAUTHORIZED);
        }
        Optional<String> extracted = OpaqueTokenCodec.extractBearerToken(rawHeader);
        if (extracted.isEmpty()) {
            return TokenResolution.failed(ErrorCode.SESSION_EXPIRED);
        }
        String token = extracted.get();
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return TokenResolution.failed(ErrorCode.SESSION_EXPIRED);
        }

        UUID userId = UUID.fromString(loginId.toString());
        SysUserEntity user = sysUserMapper.selectActiveById(userId);
        if (user == null) {
            StpUtil.logout(userId.toString());
            authRbacCache.evict(userId);
            return TokenResolution.failed(ErrorCode.SESSION_EXPIRED);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            return TokenResolution.failed(ErrorCode.ACCOUNT_BANNED);
        }

        if (renewSession && saTokenConfig.getTimeout() > 0) {
            try {
                StpUtil.renewTimeout(token, saTokenConfig.getTimeout());
            } catch (SaTokenException ex) {
                return TokenResolution.failed(ErrorCode.SESSION_EXPIRED);
            }
        }

        AuthRbacCache.RbacSnapshot rbac = authRbacCache.loadForUser(userId);
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, user.getUsername(), Set.copyOf(rbac.roles()), Set.copyOf(rbac.permissions()));
        return TokenResolution.active(new AuthenticatedSession(principal, token));
    }

    record AuthenticatedSession(AuthenticatedUser user, String token) {}

    private record TokenResolution(AuthenticatedSession session, ErrorCode failureCode) {
        static TokenResolution active(AuthenticatedSession session) {
            return new TokenResolution(session, null);
        }

        static TokenResolution failed(ErrorCode failureCode) {
            return new TokenResolution(null, failureCode);
        }
    }
}
