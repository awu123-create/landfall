package com.landfall.module.social.internal;

import com.landfall.common.api.ApiResponse;
import com.landfall.common.openapi.OpenApiConfig;
import com.landfall.module.auth.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "social", description = "社交关注关系")
@RestController
@RequestMapping("/api/v1/users")
class FollowController {

    private final FollowService followService;
    private final AuthContext authContext;

    FollowController(FollowService followService, AuthContext authContext) {
        this.followService = followService;
        this.authContext = authContext;
    }

    @Operation(summary = "关注用户")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
    @PostMapping("/{userId}/follow")
    ApiResponse<FollowStateResp> follow(@PathVariable UUID userId) {
        return ApiResponse.ok(followService.follow(authContext.requireUserId(), userId));
    }

    @Operation(summary = "取消关注用户")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
    @DeleteMapping("/{userId}/follow")
    ApiResponse<FollowStateResp> unfollow(@PathVariable UUID userId) {
        return ApiResponse.ok(followService.unfollow(authContext.requireUserId(), userId));
    }
}
