package com.laa.llm;

/** Every provider in the route's chain failed or was unusable. Maps to HTTP 503. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }
}
