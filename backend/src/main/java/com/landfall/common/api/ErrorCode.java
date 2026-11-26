package com.landfall.common.api;

import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码枚举。
 *
 * <p>完整 5 位大段与百位子域分配表、HTTP 日志责任分级与退役码禁复用规则见 {@code docs/standards/api-contract.md}；
 * 本枚举仅登记当前代码树内有真实抛出或映射方的错误码。
 */
public enum ErrorCode {
    BAD_REQUEST(10400, HttpStatus.BAD_REQUEST, "请求参数不合法"),
    NOT_FOUND(10404, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(10405, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    INTERNAL_ERROR(10500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public String resolveMessage(String customMessage) {
        return (customMessage == null || customMessage.isBlank()) ? defaultMessage : customMessage;
    }
}
