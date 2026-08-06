package com.assignment.urlshortener.exception;

public class ShortUrlAlreadyInactiveException extends RuntimeException {

    public ShortUrlAlreadyInactiveException(String shortCode) {
        super("Short URL already inactive: " + shortCode);
    }
}
