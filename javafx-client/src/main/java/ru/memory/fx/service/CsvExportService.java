package ru.memory.fx.service;

import ru.memory.fx.data.JdbcOrderGateway.OrderRow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Exports a user-facing order view without exposing database implementation details. */
public final class CsvExportService {
    private CsvExportService() {}

    public static void writeOrders(Path target, List<OrderRow> orders, DateTimeFormatter dateTimeFormatter)
            throws IOException {
        StringBuilder csv = new StringBuilder("Номер;Клиент;Создан;Статус;Услуги;Сумма\n");
        for (OrderRow order : orders) {
            csv.append(order.id()).append(';')
                    .append(value(order.clientName())).append(';')
                    .append(order.createdAt().format(dateTimeFormatter)).append(';')
                    .append(value(order.status().label())).append(';')
                    .append(value(order.services())).append(';')
                    .append(order.total()).append('\n');
        }
        Files.writeString(target, "\uFEFF" + csv, StandardCharsets.UTF_8);
    }

    private static String value(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return safe.indexOf(';') >= 0 || safe.indexOf('\"') >= 0 || safe.indexOf('\n') >= 0
                ? "\"" + safe + "\""
                : safe;
    }
}
