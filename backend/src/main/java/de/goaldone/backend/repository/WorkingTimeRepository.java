package de.goaldone.backend.repository;

import de.goaldone.backend.entity.WorkingTimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface WorkingTimeRepository extends JpaRepository<WorkingTimeEntity, UUID> {

    @Query("""
            SELECT CASE WHEN COUNT(wt) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity wt
            WHERE wt.userIdentityId = :userIdentityId
              AND wt.startTime < :endTime
              AND wt.endTime > :startTime
            """)
    boolean existsOverlappingSlot(
        @Param("userIdentityId") UUID userIdentityId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    @Query("""
            SELECT CASE WHEN COUNT(wt) > 0 THEN true ELSE false END
            FROM WorkingTimeEntity wt
            WHERE wt.userIdentityId = :userIdentityId
              AND wt.id != :excludedId
              AND wt.startTime < :endTime
              AND wt.endTime > :startTime
            """)
    boolean existsOverlappingSlotExcluding(
            @Param("userIdentityId") UUID userIdentityId,
            @Param("excludedId") UUID excludedId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}

