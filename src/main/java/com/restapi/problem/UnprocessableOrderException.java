package com.restapi.problem;

public class UnprocessableOrderException extends RuntimeException {
    public UnprocessableOrderException(String message) {
        super(message);
    }
}
