package com.laa.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.laa.llm.LlmUnavailableException;
import com.laa.llm.StructuredOutputException;
import com.laa.security.SecretRedactor;

/** RFC 9457 problem responses. Every message passes through the secret redactor. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        ProblemDetail pd = problem(e.status(), e.getMessage(), e.errors());
        if (e.code() != null) pd.setProperty("code", e.code());
        return pd;
    }

    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail unavailable(LlmUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), List.of());
    }

    @ExceptionHandler(StructuredOutputException.class)
    ProblemDetail badOutput(StructuredOutputException e) {
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage(), e.errors());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    /** A body that went past the size cap while it was read (one sent without a length) is 413, like the rest. */
    @Override
    protected org.springframework.http.ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, org.springframework.http.HttpHeaders headers,
            org.springframework.http.HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof RequestSizeLimit.TooLarge) {
                return org.springframework.http.ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                        .body(problem(HttpStatus.CONTENT_TOO_LARGE, RequestSizeLimit.TOO_LARGE, List.of()));
            }
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", List.of());
    }

    private static ProblemDetail problem(HttpStatus status, String message, List<String> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, SecretRedactor.redact(message));
        if (!errors.isEmpty()) pd.setProperty("errors", errors.stream().map(SecretRedactor::redact).toList());
        return pd;
    }
}
