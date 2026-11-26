package com.landfall.common.api;

import com.landfall.common.trace.TraceIdContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public record ApiResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "业务状态码，0 表示成功") int code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "人类可读提示信息") String message,
        @Schema(description = "业务数据载荷") T data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "全链路追踪 ID") String traceId) {

    public static final int SUCCESS_CODE = 0;
    public static final String SUCCESS_MESSAGE = "ok";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, TraceIdContext.current());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiResponse<>(errorCode.code(), errorCode.resolveMessage(message), null, TraceIdContext.current());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }
}
