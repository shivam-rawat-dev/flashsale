package com.enterprise.flashsale.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class SoldOutException extends RuntimeException {
    public SoldOutException(String message) {
        super(message);
    }
}