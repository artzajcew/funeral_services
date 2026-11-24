package  dao;

import  db.DatabaseConnector;
import  models.Service;

import java.sql.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ServiceDAO {

    public List<Service> getAllServices() {
        List<Service> services = new ArrayList<>();
        String sql = "SELECT * FROM services ORDER BY id";
        
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                services.add(new Service(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getDouble("price")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при получении услуг: " + e.getMessage());
        }
        return services;
    }

    public List<Service> searchServices(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        Locale ru = Locale.forLanguageTag("ru-RU");
        List<Service> all = getAllServices();
        Set<Integer> matchedIds = new LinkedHashSet<>();
        List<Service> services = new ArrayList<>();

        for (String variant : keywordVariants(keyword.trim())) {
            String needle = normalizeForSearch(variant, ru);
            for (Service service : all) {
                if (matchedIds.contains(service.getId())) {
                    continue;
                }
                String haystack = normalizeForSearch(service.getName(), ru);
                if (haystack.contains(needle)) {
                    matchedIds.add(service.getId());
                    services.add(service);
                }
            }
        }
        return services;
    }

    private static String normalizeForSearch(String value, Locale locale) {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(locale);
    }

    /** Варианты строки при разной кодировке консоли Windows (UTF-8 / CP866 / CP1251). */
    private static List<String> keywordVariants(String keyword) {
        List<String> variants = new ArrayList<>();
        variants.add(keyword);

        Charset[] charsets = {
                StandardCharsets.UTF_8,
                Charset.forName("Cp1251"),
                Charset.forName("Cp866"),
                Charset.defaultCharset()
        };

        for (Charset from : charsets) {
            for (Charset to : charsets) {
                if (from.equals(to)) {
                    continue;
                }
                try {
                    String converted = new String(keyword.getBytes(from), to);
                    if (!converted.isBlank() && !variants.contains(converted)) {
                        variants.add(converted);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return variants;
    }

    public Service getServiceById(int id) {
        String sql = "SELECT * FROM services WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Service(rs.getInt("id"), rs.getString("name"), rs.getDouble("price"));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
        return null;
    }
}