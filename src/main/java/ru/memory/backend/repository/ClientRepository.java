package ru.memory.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.memory.backend.entity.Client;
import ru.memory.backend.entity.User;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Integer> {

    Optional<Client> findByUserId(Integer userId);

    Optional<Client> findByPhone(String phone);

    Optional<Client> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhoneAndUserIdNot(String phone, Integer userId);

    Optional<Client> findByUser(User user);
}
