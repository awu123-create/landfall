package com.landfall.module.auth.internal;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import cn.dev33.satoken.strategy.SaStrategy;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.module.auth.AuthContext;
import com.landfall.module.auth.AuthenticatedUser;
import com.landfall.module.auth.UserDirectory;
import com.landfall.module.auth.UserSummary;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class AuthConfig {

    private static final String ADMIN_ACCESS_PERMISSION = "admin:access";
    private static final List<String> ADMIN_PATH_PATTERNS = List.of("/api/v1/admin", "/api/v1/admin/**");
    private static final SaHttpMethod[] POST_METHODS = {SaHttpMethod.POST};
    private static final String SESSION_ATTR = AuthConfig.class.getName() + ".SESSION";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthContext authContext() {
        return new AuthContext(AuthConfig::currentUser);
    }

    @Bean
    UserDirectory userDirectory(SysUserMapper sysUserMapper) {
        return new UserDirectory(ids -> sysUserMapper.selectActiveByIds(ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        SysUserEntity::getId,
                        user -> new UserSummary(
                                user.getId(), user.getNickname(), user.getAvatarUrl(), user.getBio()))));
    }

    @PostConstruct
    void configureOpaqueTokenGenerator() {
        SaStrategy.instance.createToken = (loginId, loginType) -> OpaqueTokenCodec.generate();
    }

    static Optional<AuthenticatedUser> currentUser() {
        return currentSession().map(AuthRequestAuthenticator.AuthenticatedSession::user);
    }

    static String requireCurrentToken() {
        return currentSession()
                .map(AuthRequestAuthenticator.AuthenticatedSession::token)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private static Optional<AuthRequestAuthenticator.AuthenticatedSession> currentSession() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(SESSION_ATTR, RequestAttributes.SCOPE_REQUEST);
        return value instanceof AuthRequestAuthenticator.AuthenticatedSession session
                ? Optional.of(session)
                : Optional.empty();
    }

    private static void setCurrentSession(AuthRequestAuthenticator.AuthenticatedSession session) {
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(SESSION_ATTR, session, RequestAttributes.SCOPE_REQUEST);
    }

    @Configuration
    @ConditionalOnBean(AuthRequestAuthenticator.class)
    static class AuthWebConfig implements WebMvcConfigurer {

        private final AuthRequestAuthenticator authenticator;

        AuthWebConfig(AuthRequestAuthenticator authenticator) {
            this.authenticator = authenticator;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            SaInterceptor interceptor = new SaInterceptor(this::authorizeRequest).isAnnotation(false);
            registry.addInterceptor(interceptor).addPathPatterns("/**");
        }

        private void authorizeRequest(Object handler) {
            SaRouter.match("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                    .stop();
            SaRouter.match(SaHttpMethod.POST)
                    .match("/api/v1/auth/register", "/api/v1/auth/login")
                    .stop();
            SaRouter.match(SaHttpMethod.GET)
                    .match("/api/v1/public/**")
                    .check(router -> authenticator.resolveOptional().ifPresent(AuthConfig::setCurrentSession))
                    .stop();
            SaRouter.match("/api/v1/**").check(router -> {
                boolean isLogout =
                        SaRouter.isMatchCurrMethod(POST_METHODS) && SaRouter.isMatchCurrURI("/api/v1/auth/logout");
                AuthRequestAuthenticator.AuthenticatedSession session = authenticator.requireActive(!isLogout);
                setCurrentSession(session);
                if (SaRouter.isMatchCurrURI(ADMIN_PATH_PATTERNS)
                        && !session.user().hasPermission(ADMIN_ACCESS_PERMISSION)) {
                    throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED);
                }
                if (handler instanceof HandlerMethod handlerMethod) {
                    SaAnnotationStrategy.instance.checkMethodAnnotation.accept(handlerMethod.getMethod());
                }
            });
        }
    }
}
