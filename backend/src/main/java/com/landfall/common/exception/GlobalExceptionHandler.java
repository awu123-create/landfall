package com.landfall.common.exception;

import cn.dev33.satoken.exception.NotPermissionException;
import com.landfall.common.api.ApiResponse;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.httpStatus().is5xxServerError()) {
            log.error("Server error business exception [code={}]", errorCode.code(), ex);
            return respond(errorCode);
        }
        return respond(errorCode, ex.getMessage());
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotPermission(NotPermissionException ex) {
        return respond(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::formatObjectError)
                .orElse(null);
        return respond(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(null);
        return respond(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        ServletRequestBindingException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return respond(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.httpStatus()).body(ApiResponse.error(errorCode));
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(ApiResponse.error(errorCode, message));
    }

    private static String formatObjectError(ObjectError error) {
        String detail = error.getDefaultMessage();
        if (error instanceof FieldError fieldError) {
            return (detail == null || detail.isBlank()) ? fieldError.getField() : fieldError.getField() + ": " + detail;
        }
        return detail;
    }
}
