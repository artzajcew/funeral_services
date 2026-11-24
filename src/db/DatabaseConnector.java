package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {
    private static final DatabaseConfig CONFIG = DatabaseConfig.load();

    private DatabaseConnector() {
    }

    public static Connection getConnection() throws SQLException {
        if (!CONFIG.isConfigured()) {
            throw new SQLException(
                    "Настройки базы данных не найдены. Создайте database.properties или задайте MEMORY_DB_URL, MEMORY_DB_USER и MEMORY_DB_PASSWORD.",
                    "08001");
        }
        return DriverManager.getConnection(CONFIG.url(), CONFIG.user(), CONFIG.password());
    }
}
