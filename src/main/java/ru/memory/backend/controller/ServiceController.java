package ru.memory.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.memory.backend.repository.ServiceRepository;
import ru.memory.backend.security.UserPrincipal;

import java.util.LinkedHashMap;
import java.util.Map;

/** Каталог услуг (любая роль, для создания заказа). */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;

    public ServiceController(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        var services = serviceRepository.findAll(org.springframework.data.domain.Sort.by("id"))
                .stream()
                .map(service -> {
                    Map<String, Object> json = new LinkedHashMap<String, Object>();
                    json.put("id", service.getId());
                    json.put("name", service.getName());
                    json.put("price", service.getPrice());
                    return json;
                })
                .toList();
        return ResponseEntity.ok(Map.of("data", services, "total", services.size()));
    }
}
