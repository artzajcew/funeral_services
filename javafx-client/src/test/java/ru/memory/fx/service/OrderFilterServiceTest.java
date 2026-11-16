package ru.memory.fx.service;

import org.junit.jupiter.api.Test;
import ru.memory.fx.data.JdbcOrderGateway.OrderRow;
import ru.memory.model.Models.OrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderFilterServiceTest {
    private final OrderRow order = new OrderRow(
            17, 4, "Анна Петрова", "+79990001122", "anna@example.test",
            LocalDateTime.of(2026, 9, 12, 10, 30), OrderStatus.IN_PROGRESS, 42_000, "Транспорт, Венок");

    @Test
    void combinesSearchStatusAndDatePeriod() {
        assertTrue(OrderFilterService.matches(
                order, "петрова", "В обработке", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        assertFalse(OrderFilterService.matches(
                order, "петрова", "Выполнен", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        assertFalse(OrderFilterService.matches(
                order, "венок", "Все статусы", LocalDate.of(2026, 9, 13), null));
    }

    @Test
    void validatesOnlyAnInvertedOptionalPeriod() {
        assertNull(OrderFilterService.validatePeriod(null, null));
        assertNull(OrderFilterService.validatePeriod(LocalDate.of(2026, 9, 1), null));
        assertFalse(OrderFilterService.validatePeriod(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)).isBlank());
    }
}
