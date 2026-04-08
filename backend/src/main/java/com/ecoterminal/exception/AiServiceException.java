package com.ecoterminal.exception;

/**
 * AI servisi erişim hatası — HTTP 503 döndürür.
 */
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) {
        super(message);
    }
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
