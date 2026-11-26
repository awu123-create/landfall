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

    UNAUTHORIZED(20001, HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(20003, HttpStatus.FORBIDDEN, "无权执行该操作"),
    ACCOUNT_BANNED(20004, HttpStatus.FORBIDDEN, "账号已被封禁"),
    SESSION_EXPIRED(20006, HttpStatus.UNAUTHORIZED, "登录会话已失效，请重新登录"),
    ADMIN_ACCESS_DENIED(20007, HttpStatus.FORBIDDEN, "无后台访问权限"),

    USERNAME_ALREADY_EXISTS(20101, HttpStatus.BAD_REQUEST, "用户名已被占用"),
    EMAIL_ALREADY_EXISTS(20102, HttpStatus.BAD_REQUEST, "邮箱已被注册"),
    INVALID_CREDENTIALS(20103, HttpStatus.BAD_REQUEST, "账号或密码错误"),
    INVALID_OLD_PASSWORD(20104, HttpStatus.BAD_REQUEST, "原密码错误"),

    // 包 2 媒体上传专用错误码 (301xx)
    FILE_EMPTY(30101, HttpStatus.BAD_REQUEST, "上传的文件不能为空"),
    FILE_TOO_LARGE(30102, HttpStatus.BAD_REQUEST, "文件大小超出限制"),
    INVALID_FILE_EXTENSION(30103, HttpStatus.BAD_REQUEST, "不支持的文件扩展名"),
    INVALID_FILE_HEADER(30104, HttpStatus.BAD_REQUEST, "文件内容与扩展名不符（魔数校验失败）"),

    // 包 5 社交与关注 (500xx)
    CANNOT_FOLLOW_SELF(50001, HttpStatus.BAD_REQUEST, "不能关注自己"),
    USER_NOT_FOUND(50002, HttpStatus.NOT_FOUND, "目标用户不存在");

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
