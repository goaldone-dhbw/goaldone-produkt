package de.goaldone.backend.repository;

import de.goaldone.backend.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findByAccountId(UUID accountId);

    Optional<TaskEntity> findByIdAndAccountId(UUID id, UUID accountId);
}
