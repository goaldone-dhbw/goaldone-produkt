package de.goaldone.backend.repository;

import de.goaldone.backend.entity.TaskEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    @EntityGraph(attributePaths = "dependencies")
    List<TaskEntity> findAllByAccountIdOrderByIdAsc(UUID accountId);

    @EntityGraph(attributePaths = "dependencies")
    Optional<TaskEntity> findByIdAndAccountId(UUID id, UUID accountId);

    List<TaskEntity> findAllByIdInAndAccountId(Collection<UUID> ids, UUID accountId);
}
