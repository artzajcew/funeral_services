package ru.memory.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ru.memory.backend.service.ApiError;

import java.util.Map;

/**
 * Глобальная обработка ошибок: все ответы об ошибках имеют формат
 * { "error": "Человекочитаемое описание проблемы" } — как у прежнего
 * бэкенда на JDK HttpServer (см. docs/API.md).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Ошибки бизнес-логики с нужным HTTP-статусом. */
    @ExceptionHandler(ApiError.class)
    public ResponseEntity<Map<String, Object>> handleApiError(ApiError e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    /** Некорректный JSON в теле запроса. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return badRequest("Некорректный JSON: "
                + (e.getMessage() == null ? "тело запроса не разобрано" : shortMessage(e)));
    }

    /** Неверный тип параметра (например, буквы в ?page=). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return badRequest("Параметр '" + e.getName() + "' имеет неверный формат");
    }

    /** Отсутствует обязательный параметр. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException e) {
        return badRequest("Отсутствует параметр '" + e.getParameterName() + "'");
    }

    /** Неверный HTTP-метод для маршрута. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Метод " + e.getMethod()
                        + " не поддерживается для этого маршрута"));
    }

    /** Неизвестный маршрут: /api/... с ошибочным разделом. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(Map.of(
                "error", "Маршрут не найден. API доступен по адресу /api/..."));
    }

    /** Непредвиденная ошибка сервера. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        System.err.println("[error] Внутренняя ошибка: " + e);
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of(
                "error", "Внутренняя ошибка сервера: " + shortMessage(e)));
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
