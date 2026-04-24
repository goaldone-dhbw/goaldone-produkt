package de.goaldone.backend.controller;

import de.goaldone.backend.api.SchedulesApi;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.MultiAccountScheduleResponse;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.ScheduleService;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class ScheduleController implements SchedulesApi {

    private final ScheduleService scheduleService;
    private final UserIdentityService userIdentityService;
    private final CurrentUserResolver currentUserResolver;


    private List<UserAccountEntity> getAccountsLinkedToIdentity() {
        var jwt = currentUserResolver.extractJwt();
        return userIdentityService.
                findAccountsForIdentity(userIdentityService.findIdentityFromAccount(jwt));
    }

    /**
     *
     * @param generateScheduleRequest  (required)
     * @return Schedule for multiple accounts
     */
    @Override
    public ResponseEntity<MultiAccountScheduleResponse> generateAllAccountsSchedule(GenerateScheduleRequest generateScheduleRequest) {

        List<UUID> accountIds = getAccountsLinkedToIdentity().
                stream()
                .map(UserAccountEntity::getUserIdentityId)
                .toList();

        List<ScheduleResponse> scheduleResponses = scheduleService.generateMultiAccountSchedule(
                accountIds, generateScheduleRequest, 10000
        );


        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<MultiAccountScheduleResponse> getAllAccountsSchedule(LocalDate from, LocalDate to) {
        // Validate goaldone user and its connected accounts using ids

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     *
     * @param accountId Account for which the schedule will be generated(required)
     * @param generateScheduleRequest  (required)
     * @return Schedule for the given account
     */
    @Override
    public ResponseEntity<ScheduleResponse> generateSingleAccountSchedule(UUID accountId, GenerateScheduleRequest generateScheduleRequest) {
        ScheduleResponse scheduleResponse = scheduleService.generateSchedule(accountId, generateScheduleRequest);
        return ResponseEntity.status(201).body(scheduleResponse);
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSingleAccountSchedule(UUID accountId, LocalDate from, LocalDate to) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
