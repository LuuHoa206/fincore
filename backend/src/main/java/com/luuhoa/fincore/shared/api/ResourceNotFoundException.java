package com.luuhoa.fincore.shared.api;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
