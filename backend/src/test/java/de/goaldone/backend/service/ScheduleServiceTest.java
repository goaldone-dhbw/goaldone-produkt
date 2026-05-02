package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ScheduleServiceTest {

    @Mock
    private TasksService taskService;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private Jwt mockJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
    }

    private GenerateScheduleRequest createGenerateScheduleRequest(LocalDate fromDate) {
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(fromDate);
        return request;
    }

    private UserAccountEntity createUserAccount(UUID accountId, String zitadelSub) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub(zitadelSub);
        account.setWorkingTimes(List.of());
        return account;
    }

    // =========================
    // SINGLE ACCOUNT VALIDATION
    // =========================

    @Test
    void invalidFromDate_returnsWarning() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().minusDays(1));

        UserAccountEntity account = createUserAccount(accountId, "user-1");
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertEquals(1, response.getWarnings().size());
        ScheduleWarning warning = response.getWarnings().getFirst();

        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("From date cannot be in the past"));
    }

    @Test
    void accountNotFound_returnsWarning() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        when(userAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertEquals(1, response.getWarnings().size());
        ScheduleWarning warning = response.getWarnings().getFirst();

        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("Account not found"));
    }

    @Test
    void userHasNoAccess_returnsWarning() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        UserAccountEntity account = createUserAccount(accountId, "different-user");
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertEquals(1, response.getWarnings().size());
        ScheduleWarning warning = response.getWarnings().getFirst();

        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("does not have access"));
    }

    // =========================
    // MULTI ACCOUNT VALIDATION
    // =========================

    @Test
    void emptyAccountList_returnsWarning() {
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        List<ScheduleResponse> responses =
                scheduleService.generateMultiAccountSchedule(jwt, List.of(), request, 5000);

        assertEquals(1, responses.size());
        assertEquals(1, responses.getFirst().getWarnings().size());

        ScheduleWarning warning = responses.getFirst().getWarnings().getFirst();
        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("No accounts linked to user"));
    }

    @Test
    void multiAccount_withInvalidAccount_returnsWarningForInvalid() {
        UUID validId = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();

        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        UserAccountEntity validAccount = createUserAccount(validId, "user-1");

        when(userAccountRepository.findById(validId)).thenReturn(Optional.of(validAccount));
        when(userAccountRepository.findById(invalidId)).thenReturn(Optional.empty());

        when(taskService.getTasksForAccountId(jwt, validId)).thenReturn(List.of());

        List<ScheduleResponse> responses =
                scheduleService.generateMultiAccountSchedule(jwt, List.of(validId, invalidId), request, 5000);

        assertEquals(2, responses.size());

        // One response should have a warning about the invalid account
        long warnings = responses.stream()
                .filter(r -> r.getWarnings() != null && !r.getWarnings().isEmpty() && r.getWarnings().getFirst().getMessage().contains("Account not found"))
                .count();

        assertEquals(1, warnings);
    }

    @Test
    void multiAccount_withUnauthorizedAccount_returnsWarning() {
        UUID validId = UUID.randomUUID();
        UUID unauthorizedId = UUID.randomUUID();

        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        UserAccountEntity valid = createUserAccount(validId, "user-1");
        UserAccountEntity unauthorized = createUserAccount(unauthorizedId, "other-user");

        when(userAccountRepository.findById(validId)).thenReturn(Optional.of(valid));
        when(userAccountRepository.findById(unauthorizedId)).thenReturn(Optional.of(unauthorized));

        when(taskService.getTasksForAccountId(jwt, validId)).thenReturn(List.of());

        List<ScheduleResponse> responses =
                scheduleService.generateMultiAccountSchedule(jwt, List.of(validId, unauthorizedId), request, 5000);

        assertEquals(2, responses.size());

        long warnings = responses.stream()
                .filter(r -> r.getWarnings() != null && !r.getWarnings().isEmpty() && r.getWarnings().getFirst().getMessage().contains("does not have access"))
                .count();

        assertEquals(1, warnings);
    }

    @Test
    void validateRequest_allValid_noException() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDate futureDate = LocalDate.now().plusDays(1);
        GenerateScheduleRequest request = createGenerateScheduleRequest(futureDate);

        UserAccountEntity account = createUserAccount(accountId, "user-1");
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Should not throw
        assertDoesNotThrow(() -> scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000));
    }

    @Test
    void validateRequest_pastDate_returnsWarningResponse() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDate pastDate = LocalDate.now().minusDays(1);
        GenerateScheduleRequest request = createGenerateScheduleRequest(pastDate);

        UserAccountEntity account = createUserAccount(accountId, "user-1");
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertNotNull(response);
        assertEquals(1, response.getWarnings().size());

        ScheduleWarning warning = response.getWarnings().getFirst();
        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("From date cannot be in the past"));
    }

    @Test
    void validateRequest_accountNotExists_returnsWarningResponse() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        when(userAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertNotNull(response);
        assertEquals(1, response.getWarnings().size());

        ScheduleWarning warning = response.getWarnings().getFirst();
        assertTrue(warning.getMessage().contains("Account not found"));
    }

    @Test
    void validateRequest_userUnauthorized_returnsWarningResponse() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDate.now().plusDays(1));

        UserAccountEntity account = createUserAccount(accountId, "different-user-id");
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertNotNull(response);
        assertEquals(1, response.getWarnings().size());

        ScheduleWarning warning = response.getWarnings().getFirst();
        assertTrue(warning.getMessage().contains("does not have access to account"));
    }
}
