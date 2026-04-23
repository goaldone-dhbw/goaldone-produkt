package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.exception.WorkingTimeAccessDeniedException;
import de.goaldone.backend.exception.WorkingTimeOverlapException;
import de.goaldone.backend.exception.WorkingTimeValidationException;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingTimesServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private WorkingTimeRepository workingTimeRepository;

    @InjectMocks
    private WorkingTimesService workingTimesService;

    @Test
    void createWorkingTime_happyPath_savesAndReturnsResponse() {
        UUID identityId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(identityId);

        UserAccountEntity targetAccount = new UserAccountEntity();
        targetAccount.setId(targetAccountId);
        targetAccount.setUserIdentityId(identityId);
        targetAccount.setOrganizationId(organizationId);

        OffsetDateTime start = OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 4, 20, 17, 0, 0, 0, ZoneOffset.UTC);
        WorkingTimeCreateRequest request = new WorkingTimeCreateRequest(targetAccountId, start, end);

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findByIdAndUserIdentityId(targetAccountId, identityId)).thenReturn(Optional.of(targetAccount));
        when(workingTimeRepository.existsOverlappingSlot(identityId, start.toInstant(), end.toInstant())).thenReturn(false);
        when(workingTimeRepository.save(any(WorkingTimeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkingTimeResponse response = workingTimesService.createWorkingTime(currentAccountId, request);

        assertNotNull(response.getId());
        assertEquals(targetAccountId, response.getAccountId());
        assertEquals(organizationId, response.getOrganizationId());
        assertEquals(start, response.getStartTime());
        assertEquals(end, response.getEndTime());

        ArgumentCaptor<WorkingTimeEntity> captor = ArgumentCaptor.forClass(WorkingTimeEntity.class);
        verify(workingTimeRepository).save(captor.capture());
        assertEquals(identityId, captor.getValue().getUserIdentityId());
    }

    @Test
    void createWorkingTime_endEqualsStart_throwsValidationException() {
        UUID accountId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.of(2026, 4, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        WorkingTimeCreateRequest request = new WorkingTimeCreateRequest(accountId, start, start);

        assertThrows(WorkingTimeValidationException.class,
            () -> workingTimesService.createWorkingTime(UUID.randomUUID(), request));

        verify(workingTimeRepository, never()).save(any());
    }

    @Test
    void createWorkingTime_accountNotInIdentity_throwsAccessDenied() {
        UUID identityId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(identityId);

        OffsetDateTime start = OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 4, 20, 17, 0, 0, 0, ZoneOffset.UTC);
        WorkingTimeCreateRequest request = new WorkingTimeCreateRequest(targetAccountId, start, end);

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findByIdAndUserIdentityId(targetAccountId, identityId)).thenReturn(Optional.empty());

        assertThrows(WorkingTimeAccessDeniedException.class,
            () -> workingTimesService.createWorkingTime(currentAccountId, request));

        verify(workingTimeRepository, never()).save(any());
    }

    @Test
    void createWorkingTime_overlapsAcrossCompanies_throwsOverlapException() {
        UUID identityId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(identityId);

        UserAccountEntity targetAccount = new UserAccountEntity();
        targetAccount.setId(targetAccountId);
        targetAccount.setUserIdentityId(identityId);
        targetAccount.setOrganizationId(UUID.randomUUID());

        OffsetDateTime start = OffsetDateTime.of(2026, 4, 20, 11, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 4, 20, 15, 0, 0, 0, ZoneOffset.UTC);
        WorkingTimeCreateRequest request = new WorkingTimeCreateRequest(targetAccountId, start, end);

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findByIdAndUserIdentityId(targetAccountId, identityId)).thenReturn(Optional.of(targetAccount));
        when(workingTimeRepository.existsOverlappingSlot(eq(identityId), eq(start.toInstant()), eq(end.toInstant()))).thenReturn(true);

        assertThrows(WorkingTimeOverlapException.class,
            () -> workingTimesService.createWorkingTime(currentAccountId, request));

        verify(workingTimeRepository, never()).save(any());
    }
}
