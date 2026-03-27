package com.hsd.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * 统一处理控制器层抛出的异常，返回标准化的错误响应。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验失败异常
     * 
     * 当 @Valid 注解校验失败时抛出此异常，
     * 返回具体的字段错误信息。
     * 
     * @param ex 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("[GlobalExceptionHandler] 参数校验失败: {}", errorMessage);

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", errorMessage);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理运行时异常
     * 
     * @param ex 运行时异常
     * @return 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("[GlobalExceptionHandler] 运行时异常", ex);

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", ex.getMessage() != null ? ex.getMessage() : "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理其他异常
     * 
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        log.error("[GlobalExceptionHandler] 系统异常", ex);

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "系统错误，请稍后重试");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
