package com.ecoterminal.exception;

import org.springframework.http.HttpStatus;

/**
 * Uygulama seviyesi iş kuralı ihlalleri için genel exception.
 * HTTP status kodu ile birlikte taşınır.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException(resource + " bulunamadı", HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
