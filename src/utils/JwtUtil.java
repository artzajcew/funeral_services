package utils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Выпуск и проверка JWT (HS256) без внешних зависимостей.
 *
 * Формат: base64url(header).base64url(payload).base64url(HMAC-SHA256)
 * Payload: { "sub": id, "login": ..., "role": ..., "iat": ..., "exp": ... }
 *
 * Секрет берётся из (по убыванию приоритета):
 *   1) переменная окружения MEMORY_JWT_SECRET;
 *   2) системное свойство memory.jwt.secret;
 *   3) ключ jwt.secret из database.properties;
 *   4) автоматически сгенерированный секрет в файле .jwt-secret
 *      (создаётся один раз, токены переживают перезапуск процесса).
 *
 * Рекомендуется для продакшена задать свой секрет через MEMORY_JWT_SECRET.
 */
public final class JwtUtil {

    public static final long TOKEN_TTL_SECONDS = 24 * 60 * 60; // 24 часа

    private static volatile String cachedSecret;
    private static final SecureRandom RANDOM = new SecureRandom();

    private JwtUtil() {
    }

    // ================== Токен ==================

    public static final class Claims {
        public final int userId;
        public final String login;
        public final String role;
        public final long issuedAt;
        public final long expiresAt;

        Claims(int userId, String login, String role, long issuedAt, long expiresAt) {
            this.userId = userId;
            this.login = login;
            this.role = role;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    public static String createToken(int userId, String login, String role) {
        long now = System.currentTimeMillis() / 1000L;
        String header = Json.write(Json.obj("alg", "HS256", "typ", "JWT"));
        String payload = Json.write(Json.obj(
                "sub", userId,
                "login", login,
                "role", role,
                "iat", now,
                "exp", now + TOKEN_TTL_SECONDS));
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(hmacSha256(signingInput, secret()));
        return signingInput + "." + signature;
    }

    /** Возвращает claims или null, если токен недействителен или истёк. */
    public static Claims verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            byte[] expected = hmacSha256(parts[0] + "." + parts[1], secret());
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, actual)) {
                return null;
            }
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            java.util.Map<String, Object> claims = Json.parseObject(payloadJson);

            Long sub = Json.getLong(claims, "sub");
            String login = Json.getString(claims, "login");
            String role = Json.getString(claims, "role");
            Long exp = Json.getLong(claims, "exp");
            Long iat = Json.getLong(claims, "iat");
            if (sub == null || exp == null) {
                return null;
            }
            if (exp < System.currentTimeMillis() / 1000L) {
                return null;
            }
            return new Claims(sub.intValue(), login, role,
                    iat == null ? 0L : iat, exp);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ================== Секрет ==================

    private static String secret() {
        String local = cachedSecret;
        if (local == null) {
            synchronized (JwtUtil.class) {
                if (cachedSecret == null) {
                    cachedSecret = loadSecret();
                }
                local = cachedSecret;
            }
        }
        return local;
    }

    private static String loadSecret() {
        String fromEnv = System.getenv("MEMORY_JWT_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.strip();
        }
        String fromProperty = System.getProperty("memory.jwt.secret");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.strip();
        }
        for (Path candidate : configCandidates()) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                Properties props = new Properties();
                props.load(reader);
                String value = props.getProperty("jwt.secret");
                if (value != null && !value.isBlank()) {
                    return value.strip();
                }
            } catch (IOException ignored) {
            }
        }
        return createGeneratedSecret();
    }

    /** Генерирует секрет и сохраняет его в .jwt-secret рядом с базой. */
    private static String createGeneratedSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        String secret = hex.toString();
        for (Path dir : storageDirs()) {
            try {
                Path file = dir.resolve(".jwt-secret");
                if (Files.notExists(file)) {
                    Files.writeString(file, secret, StandardCharsets.UTF_8);
                } else {
                    String existing = Files.readString(file, StandardCharsets.UTF_8).strip();
                    if (!existing.isBlank()) {
                        return existing;
                    }
                    Files.writeString(file, secret, StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
                // попробуем следующий каталог
            }
        }
        // Если сохранить не удалось — секрет живёт до перезапуска.
        return secret;
    }

    private static List<Path> configCandidates() {
        String configured = System.getProperty("memory.db.config");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("MEMORY_DB_CONFIG");
        }
        if (configured != null && !configured.isBlank()) {
            return List.of(Paths.get(configured.strip()));
        }
        return List.of(Paths.get("database.properties"),
                Paths.get("..", "database.properties"));
    }

    private static List<Path> storageDirs() {
        List<Path> candidates = configCandidates();
        List<Path> dirs = new java.util.ArrayList<>();
        for (Path candidate : candidates) {
            dirs.add(candidate.toAbsolutePath().getParent());
        }
        dirs.add(Paths.get("."));
        return dirs;
    }

    // ================== Криптография ==================

    private static byte[] hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подписать токен", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
