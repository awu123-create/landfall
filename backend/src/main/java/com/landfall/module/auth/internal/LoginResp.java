package com.landfall.module.auth.internal;

import io.swagger.v3.oas.annotations.media.Schema;

record LoginResp(
        @Schema(description = "不透明会话令牌（OpaqueToken）", requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(description = "当前登录用户信息与权限集合", requiredMode = Schema.RequiredMode.REQUIRED) AuthUserResp user) {}
