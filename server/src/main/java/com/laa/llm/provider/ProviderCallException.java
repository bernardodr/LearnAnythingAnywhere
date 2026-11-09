package com.laa.llm.provider;

/** A classified provider failure. Messages must already be redacted. */
public class ProviderCallException extends RuntimeException {
    private final ErrorKind kind;
    private final int status;

    public ProviderCallException(ErrorKind kind, String message) {
        this(kind, message, 0);
    }

    /** {@code status} is the HTTP status the provider returned, or 0 if none. */
    public ProviderCallException(ErrorKind kind, String message, int status) {
        super(message);
        this.kind = kind;
        this.status = status;
    }

    public ErrorKind kind() {
        return kind;
    }

    public int status() {
        return status;
    }
}
