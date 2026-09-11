package com.recruitinbox.common.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders Spring Security's own auth failures (no credentials, bad CSRF token)
 * with the same {@link ErrorResponse} envelope the {@code @RestControllerAdvice}
 * uses, so clients see one shape everywhere:
 * <ul>
 *   <li>not authenticated -&gt; {@code 401 UNAUTHENTICATED}</li>
 *   <li>authenticated but denied / CSRF failure -&gt; {@code 403 FORBIDDEN}</li>
 * </ul>
 */
@Component
public class RestAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Shared with {@link com.recruitinbox.common.error.GlobalExceptionHandler} so the two paths never drift. */
    public static final String UNAUTHENTICATED_MESSAGE = "authentication required";
    public static final String FORBIDDEN_MESSAGE = "not permitted";

    private final ObjectMapper mapper;

    public RestAuthErrorHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String requestId = RequestId.current();
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(RequestId.HEADER, requestId);
        mapper.writeValue(response.getWriter(),
                ErrorResponse.of(code, message, requestId, Map.of()));
    }
}
