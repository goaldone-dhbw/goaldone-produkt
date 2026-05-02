package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.exception.WorkingTimeOverlapException;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkingTimesServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private WorkingTimeRepository workingTimeRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @InjectMocks
    private WorkingTimesService workingTimesService;

    private Jwt jwt;
    private UserAccountEntity currentAccount;
    private UUID identityId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        identityId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("zitadel-sub");

        currentAccount = new UserAccountEntity();
        currentAccount.setId(accountId);
        currentAccount.setUserIdentityId(identityId);
        currentAccount.setOrganizationId(UUID.randomUUID());

        lenient().when(userAccountRepository.findByAuthUserId("zitadel-sub")).thenReturn(Optional.of(currentAccount));
    }

    @Test
    void createWorkingTime_Success() {
        WorkingTimeCreateRequest request = new WorkingTimeCreateRequest();
        request.setAccountId(accountId);
        request.setDays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        request.setStartTime("09:00");
        request.setEndTime("17:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(currentAccount));
        when(workingTimeRepository.existsOverlappingSlot(eq(identityId), any(), any(), any())).thenReturn(false);
        
        when(workingTimeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkingTimeResponse response = workingTimesService.createWorkingTime(jwt, request);

        assertThat(response).isNotNull();
        assertThat(response.getDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(response.getStartTime()).isEqualTo("09:00");
        assertThat(response.getEndTime()).isEqualTo("17:00");
        verify(workingTimeRepository).save(any(WorkingTimeEntity.class));
    }

    @Test
    void createWorkingTime_ThrowsOverlapException() {
        WorkingTimeCreateRequest request = new WorkingTimeCreateRequest();
        request.setAccountId(accountId);
        request.setDays(List.of(DayOfWeek.MONDAY));
        request.setStartTime("09:00");
        request.setEndTime("17:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(currentAccount));
        when(workingTimeRepository.existsOverlappingSlot(eq(identityId), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> workingTimesService.createWorkingTime(jwt, request))
            .isInstanceOf(WorkingTimeOverlapException.class);
    }

    @Test
    void updateWorkingTime_Success() {
        UUID wtId = UUID.randomUUID();
        WorkingTimeEntity existing = new WorkingTimeEntity();
        existing.setId(wtId);
        existing.setUserAccount(currentAccount);
        existing.setDays(Set.of(DayOfWeek.FRIDAY));
        existing.setStartTime(LocalTime.of(8, 0));
        existing.setEndTime(LocalTime.of(16, 0));
        existing.setCreatedAt(Instant.now());

        WorkingTimeUpdateRequest request = new WorkingTimeUpdateRequest();
        request.setDays(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        request.setStartTime("10:00");
        request.setEndTime("18:00");

        when(workingTimeRepository.findById(wtId)).thenReturn(Optional.of(existing));
        when(workingTimeRepository.existsOverlappingSlotExcluding(eq(identityId), eq(wtId), any(), any(), any())).thenReturn(false);
        when(workingTimeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkingTimeResponse response = workingTimesService.updateWorkingTime(jwt, wtId, request);

        assertThat(response.getDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(response.getStartTime()).isEqualTo("10:00");
        assertThat(response.getEndTime()).isEqualTo("18:00");
    }
}
