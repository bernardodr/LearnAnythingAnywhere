package com.laa.llm.provider;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.anthropic.errors.AnthropicServiceException;
import com.google.genai.errors.ApiException;
import com.openai.errors.OpenAIServiceException;

/** Maps whatever a provider SDK throws to an {@link ErrorKind}. Walks the cause chain. */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    public static ErrorKind classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ProviderCallException p) return p.kind();
            if (t instanceof TimeoutException || t instanceof SocketTimeoutException
                    || t instanceof HttpTimeoutException
                    || (t instanceof InterruptedIOException && String.valueOf(t.getMessage()).toLowerCase().contains("timeout"))) {
                return ErrorKind.TIMEOUT;
            }
            int status = statusOf(t);
            if (status == 429) return ErrorKind.RATE_LIMIT;
            if (status >= 500) return ErrorKind.SERVER_ERROR;
            if (status >= 400) return ErrorKind.PROVIDER_ERROR;
        }
        return ErrorKind.PROVIDER_ERROR;
    }

    /** HTTP status carried by a provider exception, or 0. */
    public static int statusOf(Throwable t) {
        if (t instanceof ProviderCallException p) return p.status();
        if (t instanceof OpenAIServiceException e) return e.statusCode();
        if (t instanceof AnthropicServiceException e) return e.statusCode();
        if (t instanceof ApiException e) return e.code();
        if (t instanceof RestClientResponseException e) return e.getStatusCode().value();
        if (t instanceof WebClientResponseException e) return e.getStatusCode().value();
        return 0;
    }
}
