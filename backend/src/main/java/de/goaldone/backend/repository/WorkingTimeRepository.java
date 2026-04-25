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

@Repository
public interface WorkingTimeRepository extends JpaRepository<WorkingTimeEntity, UUID> {

    @Query("""
            SELECT CASE WHEN COUNT(wt) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity wt
            JOIN wt.days d
            WHERE wt.userAccount.userIdentityId = :userIdentityId
              AND wt.startTime < :endTime
              AND wt.endTime > :startTime
              AND d IN :days
            """)
    boolean existsOverlappingSlot(
        @Param("userIdentityId") UUID userIdentityId,
        @Param("startTime")  LocalTime startTime,
        @Param("endTime")    LocalTime endTime,
        @Param("days")       Collection<DayOfWeek> days
    );

    @Query("""
            SELECT CASE WHEN COUNT(wt) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity wt
            JOIN wt.days d
            WHERE wt.userAccount.userIdentityId = :userIdentityId
              AND wt.id != :excludedId
              AND wt.startTime < :endTime
              AND wt.endTime > :startTime
              AND d IN :days
            """)
    boolean existsOverlappingSlotExcluding(
            @Param("userIdentityId") UUID userIdentityId,
            @Param("excludedId")  UUID excludedId,
            @Param("startTime")   LocalTime startTime,
            @Param("endTime")     LocalTime endTime,
            @Param("days")        Collection<DayOfWeek> days
    );

    @Query("""
            SELECT wt
            FROM WorkingTimeEntity wt
            WHERE wt.userAccount.userIdentityId = :userIdentityId
            ORDER BY wt.startTime ASC
            """)
    List<WorkingTimeEntity> findAllByUserAccountUserIdentityId(
            @Param("userIdentityId") UUID userIdentityId
    );

    @Query("""
            SELECT CASE WHEN COUNT(w1) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity w1
            JOIN w1.days d1,
                 WorkingTimeEntity w2
            JOIN w2.days d2
            WHERE w1.userAccount.userIdentityId = :userIdentityId
              AND w2.userAccount.userIdentityId = :userIdentityId
              AND w1.id < w2.id
              AND w1.startTime < w2.endTime
              AND w1.endTime > w2.startTime
              AND d1 = d2
            """)
    boolean hasConflictsForIdentity(@Param("userIdentityId") UUID userIdentityId);
}
