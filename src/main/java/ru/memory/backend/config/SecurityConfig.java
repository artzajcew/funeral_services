package ru.memory.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import ru.memory.backend.security.JwtAuthFilter;

/**
 * Настройка безопасности: все /api/** защищены JWT, кроме публичных
 * разделов (health, регистрация, вход). Сессии не создаются —
 * приложение полностью stateless, как того требует схема
 * «React → REST → Spring Boot».
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ru.memory.backend.service.AuditService auditService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          ru.memory.backend.service.AuditService auditService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.auditService = auditService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Публичные эндпоинты
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api", "/api/").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // Аутентификация
                        .requestMatchers("/api/auth/**").authenticated()
                        // Каталог услуг — любая роль
                        .requestMatchers("/api/services").authenticated()
                        // Массовые операции, CSV, архив — только админ (специфичные
                        // пути до общего /api/orders/**)
                        .requestMatchers("/api/orders/bulk", "/api/orders/export", "/api/orders/import")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/archive", "/api/orders/*/restore")
                        .hasRole("ADMIN")
                        // Заказы — любая роль (клиент видит только свои)
                        .requestMatchers("/api/orders/**").authenticated()
                        // Аналитика, аудит, управление пользователями — только админ
                        .requestMatchers("/api/analytics/**", "/api/audit/**", "/api/users/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json; charset=utf-8");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"error\": \"Требуется заголовок Authorization: Bearer <token>\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            // Фиксируем отказ в доступе для уже авторизованных
                            // пользователей (как в прежнем бэкенде).
                            var auth = org.springframework.security.core.context
                                    .SecurityContextHolder.getContext().getAuthentication();
                            if (auth != null
                                    && auth.getPrincipal() instanceof ru.memory.backend.security.UserPrincipal principal) {
                                auditService.logNow(principal.getUserId(), "ACCESS_DENIED",
                                        "ORDER", null,
                                        "Отказ в доступе: " + request.getMethod() + " "
                                                + request.getRequestURI());
                            }
                            response.setStatus(403);
                            response.setContentType("application/json; charset=utf-8");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"error\": \"Действие доступно только администратору\"}");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS для web-интерфейса (React dev-сервер и продакшен-статика). */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
