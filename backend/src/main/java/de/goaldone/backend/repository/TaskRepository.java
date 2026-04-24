package de.goaldone.backend.repository;

import de.goaldone.backend.entity.TaskEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link TaskEntity}.
 * This repository provides standard CRUD operations and custom query methods for tasks,
 * often including eager loading of task dependencies.
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    /**
     * Finds all tasks associated with a specific account ID, ordered by ID ascending.
     * Uses an {@link EntityGraph} to eagerly fetch task dependencies.
     *
     * @param accountId The UUID of the account.
     * @return A list of {@link TaskEntity} objects.
     */
    @EntityGraph(attributePaths = "dependencies")
    List<TaskEntity> findAllByAccountIdOrderByIdAsc(UUID accountId);

    /**
     * Finds a task by its ID and account ID.
     * Uses an {@link EntityGraph} to eagerly fetch task dependencies.
     *
     * @param id The UUID of the task.
     * @param accountId The UUID of the account.
     * @return An {@link Optional} containing the task entity if found, or empty otherwise.
     */
    @EntityGraph(attributePaths = "dependencies")
    Optional<TaskEntity> findByIdAndAccountId(UUID id, UUID accountId);

    /**
     * Finds all tasks with IDs in the specified collection and belonging to the given account ID.
     *
     * @param ids A collection of UUIDs to search for.
     * @param accountId The UUID of the account.
     * @return A list of {@link TaskEntity} objects.
     */
    List<TaskEntity> findAllByIdInAndAccountId(Collection<UUID> ids, UUID accountId);

    /**
     * Finds all tasks belonging to any of the account IDs in the specified collection.
     *
     * @param accountIds A collection of account UUIDs.
     * @return A list of {@link TaskEntity} objects.
     */
    List<TaskEntity> findAllByAccountIdIn(Collection<UUID> accountIds);
}
