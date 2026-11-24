package ru.memory.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.memory.backend.entity.Comment;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Integer> {

    List<Comment> findByOrderIdOrderByCreatedAtAscIdAsc(Integer orderId);
}
