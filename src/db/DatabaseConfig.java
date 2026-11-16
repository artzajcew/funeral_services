package db;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

final class DatabaseConfig {
    private final String url;
    private final String user;
    private final String password;

    private DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    static DatabaseConfig load() {
        Properties fileSettings = loadFileSettings();
        return new DatabaseConfig(
                setting("memory.db.url", "MEMORY_DB_URL", "db.url", fileSettings),
                setting("memory.db.user", "MEMORY_DB_USER", "db.user", fileSettings),
                setting("memory.db.password", "MEMORY_DB_PASSWORD", "db.password", fileSettings));
    }

    String url() { return url; }
    String user() { return user; }
    String password() { return password; }

    boolean isConfigured() {
        return !url.isBlank() && !user.isBlank() && !password.isBlank();
    }

    private static String setting(
            String property,
            String environment,
            String fileKey,
            Properties fileSettings) {
        String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) return propertyValue.strip();
        String environmentValue = System.getenv(environment);
        if (environmentValue != null && !environmentValue.isBlank()) return environmentValue.strip();
        String fileValue = fileSettings.getProperty(fileKey);
        return fileValue == null ? "" : fileValue.strip();
    }

    private static Properties loadFileSettings() {
        Properties properties = new Properties();
        for (Path candidate : configCandidates()) {
            if (!Files.isRegularFile(candidate)) continue;
            try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                properties.load(reader);
                return properties;
            } catch (IOException error) {
                throw new IllegalStateException("Не удалось прочитать настройки базы данных: " + candidate, error);
            }
        }
        return properties;
    }

    private static List<Path> configCandidates() {
        String configuredPath = System.getProperty("memory.db.config");
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = System.getenv("MEMORY_DB_CONFIG");
        }
        if (configuredPath != null && !configuredPath.isBlank()) {
            return List.of(Path.of(configuredPath.strip()));
        }
        return List.of(Path.of("database.properties"), Path.of("..", "database.properties"));
    }
}
