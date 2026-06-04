package org.xxg.backend.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.xxg.backend.backend.dto.LoginResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<LoginResponse> handleAccessDenied(Exception e) {
        String msg = e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : "无权限访问，请使用管理员账号登录";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(LoginResponse.error(msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<LoginResponse> handleRuntimeException(RuntimeException e) {
        if (e instanceof AccessDeniedException || e instanceof AuthorizationDeniedException) {
            return handleAccessDenied(e);
        }
        return ResponseEntity.badRequest().body(LoginResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LoginResponse> handleException(Exception e) {
        e.printStackTrace(); // Log the full stack trace for debugging
        return ResponseEntity.internalServerError().body(LoginResponse.error("系统错误: " + e.getMessage()));
    }
}
