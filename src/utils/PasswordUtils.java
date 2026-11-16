package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Хеширование и проверка паролей.
 *
 * Новые пароли хранятся в формате PBKDF2WithHmacSHA256 с индивидуальной
 * солью: pbkdf2$<iterations>$<saltB64>$<hashB64>.
 *
 * Устаревший формат (plain SHA-256 без соли, как в data.sql) продолжает
 * приниматься: verify() распознаёт его автоматически, а после успешного
 * входа вызывающий код может перезаписать пароль через needsUpgrade().
 */
public final class PasswordUtils {

    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String PBKDF2_PREFIX = "pbkdf2$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {
    }

    // ================== Legacy SHA-256 ==================

    /**
     * Устаревшее хеширование (plain SHA-256). Оставлено для совместимости
     * с существующей базой и консольным меню. Для новых паролей используйте
     * hashNewPassword().
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ошибка хеширования пароля", e);
        }
    }

    // ================== PBKDF2 ==================

    /** Хеширует новый пароль: PBKDF2 + случайная соль. */
    public static String hashNewPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, PBKDF2_ITERATIONS);
        return PBKDF2_PREFIX + PBKDF2_ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Проверяет пароль против сохранённого хеша.
     * Поддерживает и PBKDF2, и legacy SHA-256.
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (stored.startsWith(PBKDF2_PREFIX)) {
            String[] parts = stored.substring(PBKDF2_PREFIX.length()).split("\\$");
            if (parts.length != 3) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts[0]);
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                byte[] actual = pbkdf2(password, salt, iterations);
                return MessageDigest.isEqual(expected, actual);
            } catch (RuntimeException e) {
                return false;
            }
        }
        // Legacy: SHA-256 hex без соли — сравнение в постоянном времени.
        String candidate = hashPassword(password);
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                stored.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** true, если хеш в старом формате и его стоит перезаписать на PBKDF2. */
    public static boolean needsUpgrade(String stored) {
        return stored == null || !stored.startsWith(PBKDF2_PREFIX);
    }

    /** Случайный пароль для функции «сбросить пароль» в панели админа. */
    public static String generateRandomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iterations, KEY_BITS);
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка хеширования пароля (PBKDF2)", e);
        }
    }
}
