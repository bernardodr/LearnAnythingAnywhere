package com.laa.llm.provider;

public enum ErrorKind {
    TIMEOUT,
    RATE_LIMIT,
    SERVER_ERROR,
    PROVIDER_ERROR,
    NOT_CONFIGURED,
    INVALID_OUTPUT,
    CANCELLED
}
