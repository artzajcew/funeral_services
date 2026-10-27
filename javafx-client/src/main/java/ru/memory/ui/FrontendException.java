package ru.memory.ui;

/** Ошибка, которую консольный интерфейс может безопасно показать пользователю. */
public final class FrontendException extends RuntimeException {
    public FrontendException(String message) {
        super(message);
    }
}
