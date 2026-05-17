package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingTimeConflictServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private WorkingTimeRepository workingTimeRepository;

    @InjectMocks
    private WorkingTimeConflictService workingTimeConflictService;

    @Test
    void hasConflictsForIdentity_defaultsOverlapWhenAccountsHaveNoWorkingTimes() {
        UUID identityId = UUID.randomUUID();
        UserAccountEntity accountA = account(identityId);
        UserAccountEntity accountB = account(identityId);

        when(userAccountRepository.findAllByUserIdentityId(identityId))
            .thenReturn(List.of(accountA, accountB));
        when(workingTimeRepository.findAllByUserAccountUserIdentityId(identityId))
            .thenReturn(List.of());

        assertTrue(workingTimeConflictService.hasConflictsForIdentity(identityId));
    }

    @Test
    void hasConflictsForIdentity_manualWorkingTimeOverlapsDefaultWorkingTime() {
        UUID identityId = UUID.randomUUID();
        UserAccountEntity accountA = account(identityId);
        UserAccountEntity accountB = account(identityId);

        WorkingTimeEntity workingTime = new WorkingTimeEntity();
        workingTime.setId(UUID.randomUUID());
        workingTime.setUserAccount(accountA);
        workingTime.setDays(Set.of(DayOfWeek.MONDAY));
        workingTime.setStartTime(LocalTime.of(9, 0));
        workingTime.setEndTime(LocalTime.of(12, 0));

        when(userAccountRepository.findAllByUserIdentityId(identityId))
            .thenReturn(List.of(accountA, accountB));
        when(workingTimeRepository.findAllByUserAccountUserIdentityId(identityId))
            .thenReturn(List.of(workingTime));

        assertTrue(workingTimeConflictService.hasConflictsForIdentity(identityId));
    }

    private UserAccountEntity account(UUID identityId) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(UUID.randomUUID());
        account.setUserIdentityId(identityId);
        return account;
    }
}
