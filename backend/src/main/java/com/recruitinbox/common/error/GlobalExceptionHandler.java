package com.recruitinbox.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.recruitinbox.common.web.RequestId;
import com.recruitinbox.common.web.RestAuthErrorHandler;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.code(), ex.getMessage(), ex.fields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(ErrorCode.VALIDATION_FAILED, "request validation failed", fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        return build(ErrorCode.VALIDATION_FAILED, "request validation failed", Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception ex) {
        return build(ErrorCode.INVALID_PAYLOAD, "request body or parameters could not be parsed", Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return build(ErrorCode.PRECONDITION_REQUIRED, "If-Match header with the current version is required", Map.of());
        }
        return build(ErrorCode.VALIDATION_FAILED, "missing header: " + ex.getHeaderName(), Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(ErrorCode.VERSION_CONFLICT,
                "resource was modified by another request; reload and retry", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.INVALID_PAYLOAD, "request violates a data constraint", Map.of());
    }

    /**
     * Not authenticated (bubbled from a filter/controller rather than the
     * security entry point). Other-owner resources are 404 by contract
     * (design v1.1 section 7.4), so this stays a clean 401.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHENTICATED, RestAuthErrorHandler.UNAUTHENTICATED_MESSAGE, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, RestAuthErrorHandler.FORBIDDEN_MESSAGE, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("unhandled exception [{}]", RequestId.current(), ex);
        return build(ErrorCode.INTERNAL, "unexpected error", Map.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, Map<String, String> fields) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, message, RequestId.current(), fields));
    }
}
