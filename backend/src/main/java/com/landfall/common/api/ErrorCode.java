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
    INTERNAL_ERROR(10500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    // 包 2 媒体上传专用错误码 (301xx)
    // 包 2 媒体上传专用错误码 (301xx)
    FILE_EMPTY(30101, HttpStatus.BAD_REQUEST, "上传的文件不能为空"),
    FILE_TOO_LARGE(30102, HttpStatus.BAD_REQUEST, "文件大小超出限制"),
    INVALID_FILE_EXTENSION(30103, HttpStatus.BAD_REQUEST, "不支持的文件扩展名"),
    INVALID_FILE_HEADER(30104, HttpStatus.BAD_REQUEST, "文件内容与扩展名不符（魔数校验失败）");

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
