package com.scalelink.urlservice.domain.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String shortCode) {
        super("Short URL not found for code: " + shortCode);
    }
}
