package api;

/**
 * Ошибка API с HTTP-статусом и понятным пользователю сообщением.
 * Бросается обработчиками и конвертируется роутером в JSON-ответ.
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
}
