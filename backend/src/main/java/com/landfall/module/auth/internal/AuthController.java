package com.landfall.module.auth.internal;

import com.landfall.common.api.ApiResponse;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.common.openapi.OpenApiConfig;
import com.landfall.module.auth.AuthContext;
import com.landfall.module.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth", description = "前台认证与会话管理")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;
    private final AuthContext authContext;

    AuthController(AuthService authService, AuthContext authContext) {
        this.authService = authService;
        this.authContext = authContext;
    }

    @Operation(summary = "注册新账号")
    @PostMapping("/register")
    ApiResponse<AuthUserResp> register(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req));
    }

    @Operation(summary = "密码登录")
    @PostMapping("/login")
    ApiResponse<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req));
    }

    @Operation(summary = "单端退出登录")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
    @PostMapping("/logout")
    ApiResponse<Void> logout() {
        authService.logout(AuthConfig.requireCurrentToken());
        return ApiResponse.ok();
    }

    @Operation(summary = "校验旧密码修改新密码并全端下线")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
    @PutMapping("/password")
    ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(authContext.requireUserId(), req);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询当前登录用户信息与权限集合")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
    @GetMapping("/me")
    ApiResponse<AuthUserResp> me() {
        AuthenticatedUser currentUser =
                authContext.currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.ok(authService.currentUserProfile(currentUser));
    }
}
