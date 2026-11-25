package com.sardor.ecommerce.config;

public class RateLimitStoreUnavailableException extends RuntimeException {
    public RateLimitStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
