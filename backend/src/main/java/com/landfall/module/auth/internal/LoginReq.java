package com.landfall.module.auth.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginReq(
        @Schema(description = "用户名或邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "账号不能为空") @Size(max = 128, message = "账号长度不能超过 128 位") String account,
        @Schema(description = "登录密码", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "密码不能为空") @Size(max = 64, message = "密码长度不能超过 64 位") String password) {}
