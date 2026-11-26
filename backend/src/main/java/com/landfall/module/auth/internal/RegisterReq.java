package com.landfall.module.auth.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterReq(
        @Schema(description = "用户名（3~32 位字母、数字或下划线）", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "用户名不能为空") @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3~32 位字母、数字或下划线") String username,
        @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 128, message = "邮箱长度不能超过 128 位") String email,
        @Schema(description = "登录密码（8~64 位）", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "密码不能为空") @Size(min = 8, max = 64, message = "密码长度需在 8~64 位之间") String password,
        @Schema(description = "昵称（可选，缺省回填用户名）") @Size(max = 64, message = "昵称长度不能超过 64 位") String nickname) {}
