package dao;

/**
 * Единое unchecked-исключение DAO-слоя: оборачивает SQLException
 * и несёт понятное сообщение для HTTP-ответа (500).
 */
public class DaoException extends RuntimeException {
    public DaoException(String message) {
        super(message);
    }

    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
