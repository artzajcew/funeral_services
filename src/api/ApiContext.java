package api;

import com.sun.net.httpserver.HttpExchange;
import utils.Json;
import utils.Json.JsonException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Обёртка HTTP-запроса: сегменты пути, параметры, JSON-тело
 * и удобная отправка ответов.
 */
public class ApiContext {
    public static final int MAX_BODY_BYTES = 5 * 1024 * 1024; // 5 МБ

    private final HttpExchange exchange;
    private final String[] segments;   // части пути после /api/
    private final Map<String, String> query;
    private final String method;
    private final String requestId;

    public ApiContext(HttpExchange exchange, String requestId) {
        this.exchange = exchange;
        this.method = exchange.getRequestMethod().toUpperCase();
        this.requestId = requestId;

        String path = exchange.getRequestURI().getPath();
        String relative = path.startsWith("/api/") ? path.substring(5)
                : path.equals("/api") ? "" : path.replaceFirst("^/api", "");
        this.segments = relative.isEmpty() ? new String[0]
                : relative.split("/");

        this.query = new HashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                query.put(urlDecode(key), urlDecode(value));
            }
        }
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    // ================== Методы запроса ==================

    public HttpExchange exchange() {
        return exchange;
    }

    public String method() {
        return method;
    }

    public String requestId() {
        return requestId;
    }

    /** Количество сегментов пути после /api/. */
    public int segmentCount() {
        return segments.length;
    }

    /** Сегмент пути по индексу (0 — первый после /api/). */
    public String segment(int index) {
        return index < segments.length ? segments[index] : null;
    }

    public String query(String name) {
        return query.get(name);
    }

    public String query(String name, String defaultValue) {
        String value = query.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public Integer queryInt(String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Параметр '" + name + "' должен быть целым числом");
        }
    }

    public Double queryDouble(String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Параметр '" + name + "' должен быть числом");
        }
    }

    public LocalDate queryDate(String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiError.badRequest("Параметр '" + name + "' должен быть датой в формате YYYY-MM-DD");
        }
    }

    /** Пагинация: page (с 1) и limit (1..100). */
    public int page() {
        Integer page = queryInt("page");
        if (page != null && page < 1) {
            throw ApiError.badRequest("Параметр 'page' должен быть не меньше 1");
        }
        return page == null ? 1 : page;
    }

    public int limit() {
        Integer limit = queryInt("limit");
        if (limit != null && (limit < 1 || limit > 100)) {
            throw ApiError.badRequest("Параметр 'limit' должен быть от 1 до 100");
        }
        return limit == null ? 10 : limit;
    }

    /** Разбор JSON-тела запроса. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readJsonBody() {
        try {
            InputStream in = exchange.getRequestBody();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(chunk)) > 0) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw ApiError.badRequest("Тело запроса слишком большое");
                }
                buffer.write(chunk, 0, read);
            }
            String body = buffer.toString(StandardCharsets.UTF_8);
            if (body.isBlank()) {
                throw ApiError.badRequest("Ожидается JSON-тело запроса");
            }
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map)) {
                throw ApiError.badRequest("Ожидался JSON-объект");
            }
            return (Map<String, Object>) parsed;
        } catch (IOException e) {
            throw ApiError.badRequest("Не удалось прочитать тело запроса");
        } catch (JsonException e) {
            throw ApiError.badRequest("Некорректный JSON: " + e.getMessage());
        }
    }

    /** Чтение тела как текста (для импорта CSV). */
    public String readTextBody() {
        try {
            InputStream in = exchange.getRequestBody();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(chunk)) > 0) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw ApiError.badRequest("Тело запроса слишком большое");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiError.badRequest("Не удалось прочитать тело запроса");
        }
    }

    // ================== Ответы ==================

    public void sendJson(int status, Object data) {
        byte[] bytes = Json.write(data).getBytes(StandardCharsets.UTF_8);
        sendBytes(status, "application/json; charset=utf-8", bytes);
    }

    public void sendCsv(int status, String fileName, String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"");
        sendBytes(status, "text/csv; charset=utf-8", bytes);
    }

    private void sendBytes(int status, String contentType, byte[] bytes) {
        try {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } else {
                exchange.close();
            }
        } catch (IOException ignored) {
            // Клиент мог отключиться — это не ошибка сервера.
        }
    }

    public void sendError(int status, String message) {
        sendJson(status, Json.obj("error", message));
    }
}
