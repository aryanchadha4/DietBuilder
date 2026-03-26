package com.dietbuilder.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ensures API error responses include a {@code message} field and maps common failures to HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation error path={}: {}", req.getRequestURI(), msg);
        return build(HttpStatus.BAD_REQUEST, req.getRequestURI(), msg);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UsernameNotFoundException ex, HttpServletRequest req) {
        log.warn("User not found path={}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, req.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Bad request path={}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, req.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex, HttpServletRequest req) {
        String m = ex.getMessage();
        if (m != null && m.contains("Access denied")) {
            log.warn("Forbidden path={}: {}", req.getRequestURI(), m);
            return build(HttpStatus.FORBIDDEN, req.getRequestURI(), m);
        }
        if (m != null && m.contains("Profile not found")) {
            log.warn("Not found path={}: {}", req.getRequestURI(), m);
            return build(HttpStatus.NOT_FOUND, req.getRequestURI(), m);
        }
        log.error("Server error path={}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, req.getRequestURI(),
                m != null && !m.isBlank() ? m : ex.getClass().getSimpleName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Unhandled path={}", req.getRequestURI(), ex);
        String m = ex.getMessage();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, req.getRequestURI(),
                m != null && !m.isBlank() ? m : ex.getClass().getSimpleName());
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String path, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("path", path);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
