package com.laa.security;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/** Logback {@code %wEx}/{@code %ex}: stack traces with secrets masked (exception messages can carry them). */
public class RedactingThrowableConverter extends ExtendedThrowableProxyConverter {
    @Override
    protected String throwableProxyToString(IThrowableProxy tp) {
        String trace = super.throwableProxyToString(tp);
        return trace.isEmpty() ? trace : SecretRedactor.redact(trace);
    }
}
