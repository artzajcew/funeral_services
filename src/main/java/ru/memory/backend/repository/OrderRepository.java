package ru.memory.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.memory.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    /**
     * Поиск заказов с фильтрами, сортировкой и пагинацией
     * (клиент видит только свои заказы, сотрудник — все).
     *
     * ownerUserId — ID учётной записи клиента (только для роли CLIENT);
     * clientId    — фильтр по профилю клиента (для сотрудников);
     * archived    — false: активные, true: архивные, null: все.
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.client c
            WHERE (:ownerUserId IS NULL OR c.user.id = :ownerUserId)
              AND (:clientId IS NULL OR c.id = :clientId)
              AND (:status IS NULL OR o.status = :status)
              AND (:archived IS NULL OR o.archived = :archived)
              AND (:dateFrom IS NULL OR o.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR o.createdAt < :dateTo)
              AND (:minCost IS NULL OR o.totalCost >= :minCost)
              AND (:maxCost IS NULL OR o.totalCost <= :maxCost)
              AND (:search IS NULL
                       OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(o.status) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR (:searchNumber IS NOT NULL AND o.id = :searchNumber))
            """)
    Page<Order> searchOrders(@Param("ownerUserId") Integer ownerUserId,
                             @Param("clientId") Integer clientId,
                             @Param("status") String status,
                             @Param("archived") Boolean archived,
                             @Param("dateFrom") LocalDateTime dateFrom,
                             @Param("dateTo") LocalDateTime dateTo,
                             @Param("minCost") BigDecimal minCost,
                             @Param("maxCost") BigDecimal maxCost,
                             @Param("search") String search,
                             @Param("searchNumber") Integer searchNumber,
                             Pageable pageable);
}
