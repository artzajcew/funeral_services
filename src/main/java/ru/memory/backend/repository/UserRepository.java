package ru.memory.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.memory.backend.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);

    boolean existsByIdAndBlocked(Integer id, boolean blocked);

    /**
     * Список пользователей с профилем клиента, числом заказов
     * и временем последнего действия (для панели админа).
     */
    @Query("""
            SELECT u AS user, c AS client,
                   (SELECT COUNT(o) FROM Order o WHERE o.client = c) AS ordersCount,
                   (SELECT MAX(a.createdAt) FROM AuditEntry a WHERE a.user = u) AS lastActionAt
            FROM User u
            LEFT JOIN u.client c
            WHERE (:search IS NULL
                       OR LOWER(u.login) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(COALESCE(c.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR CAST(u.id AS string) = :search)
              AND (:role IS NULL OR u.role = :role)
              AND (:blocked IS NULL OR u.blocked = :blocked)
            ORDER BY u.id
            """)
    Page<UserListItem> searchUsers(@Param("search") String search,
                                   @Param("role") String role,
                                   @Param("blocked") Boolean blocked,
                                   Pageable pageable);

    /** Строка списка пользователей (проекция). */
    interface UserListItem {
        User getUser();

        Client getClient();

        long getOrdersCount();

        java.time.LocalDateTime getLastActionAt();
    }

    interface Client {
        Integer getId();

        String getPhone();

        String getEmail();

        String getFullName();
    }
}
