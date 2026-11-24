package ru.memory.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.memory.backend.entity.Service;

import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Integer> {

    Optional<Service> findByName(String name);
}
