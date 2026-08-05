package com.assignment.urlshortener.exception;

public class CustomAliasConflictException extends RuntimeException {

    public CustomAliasConflictException(String alias) {
        super("Custom alias already in use: " + alias);
    }
}
