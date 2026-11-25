package com.factoryflow.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String detail;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.detail = message;
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.detail = message;
    }

    public BusinessException(String message, String detail, HttpStatus status) {
        super(message);
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() { return status; }
    public String getDetail() { return detail; }
}
