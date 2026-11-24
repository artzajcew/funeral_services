package ru.memory.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.memory.backend.entity.AuditEntry;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEntry, Integer> {

    /** Выборка журнала с фильтрами и пагинацией (админ). */
    @Query("""
            SELECT a FROM AuditEntry a
            LEFT JOIN FETCH a.user
            WHERE (:userId IS NULL OR a.user.id = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR a.createdAt < :dateTo)
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditEntry> search(@Param("userId") Integer userId,
                            @Param("action") String action,
                            @Param("entityType") String entityType,
                            @Param("dateFrom") LocalDateTime dateFrom,
                            @Param("dateTo") LocalDateTime dateTo,
                            Pageable pageable);

    /** Список типов действий (для фильтров интерфейса админа). */
    @Query("SELECT DISTINCT a.action FROM AuditEntry a ORDER BY a.action")
    List<String> distinctActions();
}
