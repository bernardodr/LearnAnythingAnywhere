package com.laa.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caps every request body at {@code laa.web.max-request-size} (every LAA request is short text). A body that says it's
 * bigger is refused with 413 before anything reads it; one sent without a length stops being read at the cap, which
 * fails the request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestSizeLimit extends OncePerRequestFilter {

    static final String TOO_LARGE = "That request is too large.";

    /** Thrown when a body sent without a length goes past the cap; {@link ApiExceptionHandler} answers 413. */
    static final class TooLarge extends IOException {
        TooLarge(long max) {
            super("Request body is larger than " + max + " bytes");
        }
    }

    private final long max;

    RequestSizeLimit(@Value("${laa.web.max-request-size:256KB}") DataSize max) {
        this.max = max.toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > max) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getOutputStream().write(("{\"title\":\"Content Too Large\",\"status\":413,"
                    + "\"detail\":\"" + TOO_LARGE + "\"}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(length >= 0 ? request : new Capped(request, max), response);
    }

    /** A request whose body can't be read past {@code max} bytes. */
    private static final class Capped extends HttpServletRequestWrapper {
        private final long max;
        private ServletInputStream stream;

        Capped(HttpServletRequest request, long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new CappedStream(super.getInputStream(), max);
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class CappedStream extends ServletInputStream {
        private final ServletInputStream in;
        private final long max;
        private long read;

        CappedStream(ServletInputStream in, long max) {
            this.in = in;
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = in.read(buffer, offset, length);
            if (n > 0) count(n);
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > max) throw new TooLarge(max);
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
