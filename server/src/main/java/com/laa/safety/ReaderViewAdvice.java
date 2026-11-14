package com.laa.safety;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.laa.auth.CurrentUser;
import com.laa.llm.GenerationProfile.LinksPolicy;

import tools.jackson.databind.json.JsonMapper;

/**
 * Puts every learner-facing JSON response through the {@link ReaderView} on its way out. On by default, so a new
 * endpoint is covered without doing anything; only the owner and dev tools are left out, because they show what the
 * model wrote on purpose. Streamed replies don't come through here: they're checked as they're written.
 */
@RestControllerAdvice
class ReaderViewAdvice implements ResponseBodyAdvice<Object> {

    private final ReaderView view;
    private final JsonMapper json;

    ReaderViewAdvice(ReaderView view, JsonMapper json) {
        this.view = view;
        this.json = json;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType) && forReaders(returnType.getContainingClass());
    }

    /** The owner console and the dev endpoints show raw model output; everything else is for a reader. */
    static boolean forReaders(Class<?> controller) {
        String pkg = controller.getPackageName();
        return !pkg.startsWith("com.laa.owner") && !pkg.startsWith("com.laa.dev");
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null || body instanceof ProblemDetail) return body; // our own error messages
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) return body;
        UUID userId = CurrentUser.id(auth.getToken());
        ReaderView.Reader reader = view.of(userId);
        if (reader.policy() == LinksPolicy.OPEN) return body;
        return reader.apply(json.valueToTree(body));
    }
}
