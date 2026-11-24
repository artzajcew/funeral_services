package ru.memory.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Корень API и проверка живости. */
@RestController
public class RootController {

    @GetMapping({"/api", "/api/"})
    public ResponseEntity<Map<String, Object>> index() {
        return ResponseEntity.ok(Map.of(
                "service", "REST API бюро ритуальных услуг",
                "docs", "docs/API.md",
                "health", "/api/health"));
    }

    /** Проверка живости (без авторизации). */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
