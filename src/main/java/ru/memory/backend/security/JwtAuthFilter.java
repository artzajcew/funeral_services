package ru.memory.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.memory.backend.entity.User;
import ru.memory.backend.repository.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * JWT-фильтр: читает Authorization: Bearer <token>, проверяет подпись
 * и срок, затем загружает актуального пользователя из БД (роль и
 * блокировка действуют сразу, не дожидаясь истечения токена).
 *
 * Ошибки авторизации возвращаются в формате { "error": "..." }.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && !header.isBlank()) {
            String token = header.trim();
            if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            }
            Optional<Integer> userId = jwtService.verifyToken(token);
            if (userId.isEmpty()) {
                writeError(response, HttpStatus.UNAUTHORIZED.value(),
                        "Токен недействителен или истёк. Войдите снова.");
                return;
            }
            Optional<User> user = userRepository.findById(userId.get());
            if (user.isEmpty()) {
                writeError(response, HttpStatus.UNAUTHORIZED.value(), "Пользователь не найден");
                return;
            }
            if (user.get().isBlocked()) {
                writeError(response, HttpStatus.FORBIDDEN.value(),
                        "Учётная запись заблокирована. Обратитесь к администратору.");
                return;
            }
            var auth = new UsernamePasswordAuthenticationToken(
                    new UserPrincipal(user.get()), null,
                    new UserPrincipal(user.get()).getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\": \"" + message.replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"}");
    }
}
