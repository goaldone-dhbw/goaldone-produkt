package de.goaldone.backend.repository;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for {@link WorkingTimeEntity}.
 * Manages working time slots for users across different days and time ranges.
 */
@Repository
public interface WorkingTimeRepository extends JpaRepository<WorkingTimeEntity, UUID> {

    /**
     * Checks if there is an overlapping working time slot for the given user,
     * within the specified time range and days.
     *
     * @param userId    The UUID of the user.
     * @param startTime The start time of the slot to check.
     * @param endTime   The end time of the slot to check.
     * @param days      The collection of days to check for overlaps.
     * @return {@code true} if an overlapping slot exists, {@code false} otherwise.
     */
    @Query("""
            SELECT CASE WHEN COUNT(wt) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity wt
            JOIN wt.days d
            WHERE wt.membership.user.id = :userId
              AND wt.startTime < :endTime
              AND wt.endTime > :startTime
              AND d IN :days
            """)
    boolean existsOverlappingSlot(
        @Param("userId")    UUID userId,
        @Param("startTime") LocalTime startTime,
        @Param("endTime")   LocalTime endTime,
        @Param("days")      Collection<DayOfWeek> days
    );

    /**
     * Checks if there is an overlapping working time slot for the given user,
     * excluding a specific slot ID. Useful for updates to avoid self-conflict.
     *
     * @param userId     The UUID of the user.
     * @param excludedId The UUID of the slot to exclude from the overlap check.
     * @param startTime  The start time of the slot to check.
     * @param endTime    The end time of the slot to check.
     * @param days       The collection of days to check for overlaps.
     * @return {@code true} if an overlapping slot exists (excluding the specified one), {@code false} otherwise.
     */
    @Query("""
            SELECT CASE WHEN COUNT(wt) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity wt
            JOIN wt.days d
            WHERE wt.membership.user.id = :userId
              AND wt.id != :excludedId
              AND wt.startTime < :endTime
              AND wt.endTime > :startTime
              AND d IN :days
            """)
    boolean existsOverlappingSlotExcluding(
            @Param("userId")      UUID userId,
            @Param("excludedId")  UUID excludedId,
            @Param("startTime")   LocalTime startTime,
            @Param("endTime")     LocalTime endTime,
            @Param("days")        Collection<DayOfWeek> days
    );

    /**
     * Finds all working times for a specific user, ordered by start time ascending.
     *
     * @param userId The UUID of the user.
     * @return A list of {@link WorkingTimeEntity} objects.
     */
    @Query("""
            SELECT wt
            FROM WorkingTimeEntity wt
            WHERE wt.membership.user.id = :userId
            ORDER BY wt.startTime ASC
            """)
    List<WorkingTimeEntity> findAllByMembershipUserId(
            @Param("userId") UUID userId
    );

    /**
     * Checks if any conflicts (overlaps) exist between all working time slots for a given user.
     *
     * @param userId The UUID of the user.
     * @return {@code true} if conflicts exist, {@code false} otherwise.
     */
    @Query("""
            SELECT CASE WHEN COUNT(w1) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity w1
            JOIN w1.days d1,
                 WorkingTimeEntity w2
            JOIN w2.days d2
            WHERE w1.membership.user.id = :userId
              AND w2.membership.user.id = :userId
              AND w1.id < w2.id
              AND w1.startTime < w2.endTime
              AND w1.endTime > w2.startTime
              AND d1 = d2
            """)
    boolean hasConflictsForUser(@Param("userId") UUID userId);
}
