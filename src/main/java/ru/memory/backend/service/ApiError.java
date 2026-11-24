package ru.memory.backend.service;

/**
 * Ошибка API: HTTP-статус + человекочитаемое сообщение.
 * Глобальный обработчик превращает её в { "error": "..." } —
 * тот же формат, что был у прежнего бэкенда на JDK HttpServer.
 */
public class ApiError extends RuntimeException {

    private final int status;

    public ApiError(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiError badRequest(String message) {
        return new ApiError(400, message);
    }

    public static ApiError unauthorized(String message) {
        return new ApiError(401, message);
    }

    public static ApiError forbidden(String message) {
        return new ApiError(403, message);
    }

    public static ApiError notFound(String message) {
        return new ApiError(404, message);
    }

    public static ApiError conflict(String message) {
        return new ApiError(409, message);
    }

    public static ApiError methodNotAllowed(String message) {
        return new ApiError(405, message);
    }
}
