package de.goaldone.backend.controller;

import de.goaldone.backend.api.SchedulesApi;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.MultiAccountScheduleResponse;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.ScheduleService;
import de.goaldone.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class ScheduleController implements SchedulesApi {

    private final ScheduleService scheduleService;
    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;


    private List<MembershipEntity> getAccountsInOrganization(UUID xOrgID) {
        var jwt = currentUserResolver.extractJwt();
        // In the current multi-org model, we focus on the user's membership in the target organization.
        return List.of(userService.resolveMembership(jwt, xOrgID));
    }

    /**
     * Generates a schedule for all accounts the user has access to within the organization.
     *
     * @param xOrgID                  The organization ID context.
     * @param generateScheduleRequest The schedule generation request.
     * @return A ResponseEntity with the multi-account schedule.
     */
    @Override
    public ResponseEntity<MultiAccountScheduleResponse> generateAllAccountsSchedule(UUID xOrgID, GenerateScheduleRequest generateScheduleRequest) {
        Jwt jwt = currentUserResolver.extractJwt();

        List<UUID> accountIds = getAccountsInOrganization(xOrgID).
                stream()
                .map(MembershipEntity::getId)
                .toList();

        List<ScheduleResponse> scheduleResponses = scheduleService.generateMultiAccountSchedule(
                jwt, accountIds, generateScheduleRequest, xOrgID, 10000
        );

        MultiAccountScheduleResponse response = new MultiAccountScheduleResponse();
        response.setSchedules(scheduleResponses);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the current schedule for all accounts within the organization.
     *
     * @param from   The start date.
     * @param to     The end date.
     * @param xOrgID The organization ID context (optional).
     * @return A ResponseEntity with the current multi-account schedule.
     */
    @Override
    public ResponseEntity<MultiAccountScheduleResponse> getAllAccountsSchedule(LocalDate from, LocalDate to, UUID xOrgID) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * Generates a schedule for a single account within the organization.
     *
     * @param xOrgID                  The organization ID context.
     * @param accountId               The ID of the account (membership).
     * @param generateScheduleRequest The schedule generation request.
     * @return A ResponseEntity with the generated schedule.
     */
    @Override
    public ResponseEntity<ScheduleResponse> generateSingleAccountSchedule(@RequestHeader("X-Org-ID") UUID xOrgID, UUID accountId, GenerateScheduleRequest generateScheduleRequest) {
        Jwt jwt = currentUserResolver.extractJwt();
        ScheduleResponse scheduleResponse = scheduleService.generateSchedule(jwt, accountId, generateScheduleRequest, xOrgID);
        return ResponseEntity.status(201).body(scheduleResponse);
    }

    /**
     * Retrieves the current schedule for a single account within the organization.
     *
     * @param xOrgID    The organization ID context.
     * @param accountId The ID of the account (membership).
     * @param from      The start date.
     * @param to        The end date.
     * @return A ResponseEntity with the current schedule.
     */
    @Override
    public ResponseEntity<ScheduleResponse> getSingleAccountSchedule(@RequestHeader("X-Org-ID") UUID xOrgID, UUID accountId, LocalDate from, LocalDate to) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
