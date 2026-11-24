package ru.memory.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/**
 * Выпуск и проверка JWT (HS256).
 *
 * Секрет берётся из настройки app.jwt.secret (свойство memory.jwt.secret
 * или переменная окружения MEMORY_JWT_SECRET). Если секрет не задан,
 * генерируется временный (токены действительны до перезапуска).
 */
@Component
public class JwtService {

    public static final long TOKEN_TTL_SECONDS = 24 * 60 * 60; // 24 часа

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            // Сгенерированный секрет: токены живут до перезапуска приложения.
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
        } else {
            byte[] bytes = secret.strip().getBytes(StandardCharsets.UTF_8);
            // HS256 требует минимум 32 байт ключа.
            this.key = Keys.hmacShaKeyFor(
                    bytes.length >= 32 ? bytes : padTo32(bytes));
        }
    }

    private static byte[] padTo32(byte[] bytes) {
        byte[] padded = new byte[32];
        for (int i = 0; i < 32; i++) {
            padded[i] = bytes[i % bytes.length];
        }
        return padded;
    }

    public String createToken(int userId, String login, String role) {
        Date now = new Date();
        Date expires = new Date(now.getTime() + TOKEN_TTL_SECONDS * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("login", login)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expires)
                .signWith(key)
                .compact();
    }

    /** Возвращает ID пользователя, если токен подписан верно и не истёк. */
    public Optional<Integer> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Integer.parseInt(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
