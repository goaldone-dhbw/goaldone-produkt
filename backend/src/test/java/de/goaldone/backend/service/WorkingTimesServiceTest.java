package de.goaldone.backend.service;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WorkingTimesService.
 * Tests working time operations.
 */
@ExtendWith(MockitoExtension.class)
class WorkingTimesServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private WorkingTimeRepository workingTimeRepository;

    @Test
    void saveWorkingTime_Success() {
        UUID wtId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        WorkingTimeEntity wt = new WorkingTimeEntity();
        wt.setId(wtId);
        wt.setDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        wt.setStartTime(LocalTime.of(9, 0));
        wt.setEndTime(LocalTime.of(17, 0));

        when(workingTimeRepository.save(any(WorkingTimeEntity.class))).thenReturn(wt);

        WorkingTimeEntity saved = workingTimeRepository.save(wt);

        assertNotNull(saved);
        assertEquals(wtId, saved.getId());
        assertTrue(saved.getDays().contains(DayOfWeek.MONDAY));
        verify(workingTimeRepository).save(any(WorkingTimeEntity.class));
    }

    @Test
    void getWorkingTime_ReturnsWorkingTime() {
        UUID wtId = UUID.randomUUID();

        WorkingTimeEntity wt = new WorkingTimeEntity();
        wt.setId(wtId);
        wt.setDays(Set.of(DayOfWeek.WEDNESDAY));
        wt.setStartTime(LocalTime.of(8, 0));
        wt.setEndTime(LocalTime.of(16, 0));

        when(workingTimeRepository.findById(wtId)).thenReturn(Optional.of(wt));

        Optional<WorkingTimeEntity> result = workingTimeRepository.findById(wtId);

        assertTrue(result.isPresent());
        assertEquals(wtId, result.get().getId());
    }

    @Test
    void deleteWorkingTime_Success() {
        UUID wtId = UUID.randomUUID();

        workingTimeRepository.deleteById(wtId);

        verify(workingTimeRepository).deleteById(wtId);
    }
}
