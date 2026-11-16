package utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальная библиотека JSON без внешних зависимостей.
 *
 * Разбор: parse(String) возвращает Map<String, Object>, List<Object>,
 * String, Long, Double, Boolean или null.
 * Сериализация: write(Object) принимает те же типы (числа, строки,
 * Boolean, null, Map и Iterable/массивы).
 *
 * Строки с не-ASCII символами (кириллица) пишутся как есть — вся
 * система работает в UTF-8.
 */
public final class Json {

    private static final int MAX_DEPTH = 64;

    private Json() {
    }

    // ================== Разбор ==================

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("Пустой JSON");
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Лишние символы после JSON-значения");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("Ожидался JSON-объект");
        }
        return (Map<String, Object>) value;
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean isAtEnd() {
            return i >= s.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " (позиция " + i + ")");
        }

        void skipWhitespace() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("Слишком глубокая вложенность JSON");
            }
            if (isAtEnd()) {
                throw error("Неожиданный конец строки");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObject(depth);
                case '[': return parseArray(depth);
                case '"': return parseString();
                case 't':
                    expectWord("true");
                    return Boolean.TRUE;
                case 'f':
                    expectWord("false");
                    return Boolean.FALSE;
                case 'n':
                    expectWord("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw error("Неизвестный символ '" + c + "'");
            }
        }

        private void expectWord(String word) {
            if (i + word.length() > s.length() || !s.startsWith(word, i)) {
                throw error("Ожидалось '" + word + "'");
            }
            i += word.length();
        }

        private Map<String, Object> parseObject(int depth) {
            Map<String, Object> result = new LinkedHashMap<>();
            i++; // '{'
            skipWhitespace();
            if (!isAtEnd() && s.charAt(i) == '}') {
                i++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || s.charAt(i) != '"') {
                    throw error("Ожидалось имя ключа");
                }
                String key = parseString();
                skipWhitespace();
                if (isAtEnd() || s.charAt(i) != ':') {
                    throw error("Ожидалось ':'");
                }
                i++;
                skipWhitespace();
                result.put(key, parseValue(depth + 1));
                skipWhitespace();
                if (isAtEnd()) {
                    throw error("Не закрыт объект");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    return result;
                }
                throw error("Ожидалось ',' или '}'");
            }
        }

        private List<Object> parseArray(int depth) {
            List<Object> result = new ArrayList<>();
            i++; // '['
            skipWhitespace();
            if (!isAtEnd() && s.charAt(i) == ']') {
                i++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(parseValue(depth + 1));
                skipWhitespace();
                if (isAtEnd()) {
                    throw error("Не закрыт массив");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    return result;
                }
                throw error("Ожидалось ',' или ']'");
            }
        }

        private String parseString() {
            i++; // '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (isAtEnd()) {
                    throw error("Строка не закрыта");
                }
                char c = s.charAt(i);
                if (c == '"') {
                    i++;
                    return sb.toString();
                }
                if (c == '\\') {
                    i++;
                    if (isAtEnd()) {
                        throw error("Строка не закрыта");
                    }
                    char esc = s.charAt(i);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i + 4 >= s.length()) {
                                throw error("Некорректный \\u-экейп");
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            } catch (NumberFormatException e) {
                                throw error("Некорректный \\u-экейп");
                            }
                            i += 4;
                            break;
                        default:
                            throw error("Неизвестный экейп '\\" + esc + "'");
                    }
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
        }

        private Object parseNumber() {
            int start = i;
            if (i < s.length() && s.charAt(i) == '-') i++;
            boolean isDouble = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    i++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isDouble = true;
                    i++;
                } else {
                    break;
                }
            }
            String number = s.substring(start, i);
            try {
                if (isDouble) {
                    return Double.parseDouble(number);
                }
                return Long.parseLong(number);
            } catch (NumberFormatException e) {
                throw error("Некорректное число '" + number + "'");
            }
        }
    }

    // ================== Сериализация ==================

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(128);
        writeValue(value, sb, 0);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("Слишком глубокая вложенность при сериализации");
        }
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String s) {
            writeString(s, sb);
            return;
        }
        if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
            return;
        }
        if (value instanceof Number n) {
            writeNumber(n, sb);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeObject(map, sb, depth);
            return;
        }
        if (value instanceof Iterable<?> it) {
            writeArray(it, sb, depth);
            return;
        }
        if (value instanceof Object[] arr) {
            writeArray(java.util.Arrays.asList(arr), sb, depth);
            return;
        }
        // Любой другой объект — его строковое представление.
        writeString(value.toString(), sb);
    }

    private static void writeNumber(Number n, StringBuilder sb) {
        if (n instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append("null");
            } else {
                sb.append(d);
            }
        } else if (n instanceof Float f) {
            if (f.isNaN() || f.isInfinite()) {
                sb.append("null");
            } else {
                sb.append(f.doubleValue());
            }
        } else {
            sb.append(n);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder sb, int depth) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()), sb);
            sb.append(':');
            writeValue(entry.getValue(), sb, depth + 1);
        }
        sb.append('}');
    }

    private static void writeArray(Iterable<?> values, StringBuilder sb, int depth) {
        sb.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(value, sb, depth + 1);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ================== Удобные сборщики и доступы ==================

    /** Быстрое создание Map для ответа: Json.obj("id", 1, "name", "x"). */
    public static Map<String, Object> obj(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Нечётное число аргументов obj()");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    public static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    public static Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static Boolean getBool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
}
