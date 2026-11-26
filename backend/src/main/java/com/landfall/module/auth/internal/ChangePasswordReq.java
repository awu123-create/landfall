package com.landfall.module.auth.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ChangePasswordReq(
        @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "原密码不能为空") @Size(max = 64, message = "原密码长度不能超过 64 位") String oldPassword,
        @Schema(description = "新密码（8~64 位）", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 64, message = "新密码长度需在 8~64 位之间") String newPassword) {}
