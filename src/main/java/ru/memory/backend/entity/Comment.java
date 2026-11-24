package ru.memory.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Комментарий к заказу. Клиент пишет к своим заказам, сотрудник — к любым. */
@Entity
@Table(name = "order_comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Comment() {
    }

    public Comment(Order order, User author, String body) {
        this.order = order;
        this.author = author;
        this.body = body;
    }

    public Integer getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public User getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
