package com.laa.llm;

import java.util.List;

/** The model's output still failed validation after one corrective retry. Maps to HTTP 502. */
public class StructuredOutputException extends RuntimeException {
    private final List<String> errors;

    public StructuredOutputException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
