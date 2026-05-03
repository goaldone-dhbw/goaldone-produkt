package de.goaldone.backend.service;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.exception.WorkingTimeAccessDeniedException;
import de.goaldone.backend.exception.WorkingTimeOverlapException;
import de.goaldone.backend.exception.WorkingTimeValidationException;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeListResponse;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing working times.
 * Handles creation, update, deletion, and retrieval of working time slots within an organization.
 */
@Service
@RequiredArgsConstructor
public class WorkingTimesService {

    private final MembershipRepository membershipRepository;
    private final WorkingTimeRepository workingTimeRepository;
    private final UserService userService;

    /**
     * Creates a new working time slot.
     *
     * @param jwt     The current JWT representing the logged-in user.
     * @param request The request object containing working time details.
     * @param xOrgID  The UUID of the organization.
     * @return The created {@link WorkingTimeResponse}.
     */
    @Transactional
    public WorkingTimeResponse createWorkingTime(Jwt jwt, WorkingTimeCreateRequest request, UUID xOrgID) {
        // Resolve current membership
        MembershipEntity currentMembership = userService.resolveMembership(jwt, xOrgID);

        // Check access to target membership
        if (!userService.hasUserAccessToMembership(jwt, xOrgID, request.getAccountId())) {
            throw new WorkingTimeAccessDeniedException("The logged-in user does not have access to this account.");
        }

        // Load target membership
        MembershipEntity targetMembership = membershipRepository.findById(request.getAccountId())
                .orElseThrow(() -> new WorkingTimeAccessDeniedException("The specified membership was not found."));

        // Validate days
        List<DayOfWeek> days = request.getDays();
        if (days == null || days.isEmpty()) {
            throw new WorkingTimeValidationException("At least one day of the week must be selected.");
        }

        // Parse times
        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(request.getStartTime());
            endTime = LocalTime.parse(request.getEndTime());
        } catch (Exception e) {
            throw new WorkingTimeValidationException("Invalid time format. Expected: HH:mm");
        }

        // Validate end time > start time
        if (!endTime.isAfter(startTime)) {
            throw new WorkingTimeValidationException("End time must be after start time.");
        }

        // Check for overlaps within the user's working times
        boolean overlaps = workingTimeRepository.existsOverlappingSlot(currentMembership.getUser().getId(), startTime, endTime, days);

        if (overlaps) {
            throw new WorkingTimeOverlapException("The working time overlaps with an existing working time of the user.");
        }

        WorkingTimeEntity entity = new WorkingTimeEntity();
        entity.setId(UUID.randomUUID());
        entity.setMembership(targetMembership);
        entity.setOrganizationId(xOrgID);
        entity.setDays(new HashSet<>(days));
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setCreatedAt(Instant.now());

        WorkingTimeEntity saved = workingTimeRepository.save(entity);
        return mapToResponse(saved, false);
    }

    /**
     * Updates an existing working time slot.
     *
     * @param jwt           The current JWT representing the logged-in user.
     * @param workingTimeId The UUID of the working time slot to update.
     * @param request       The request object containing updated details.
     * @param xOrgID        The UUID of the organization.
     * @return The updated {@link WorkingTimeResponse}.
     */
    @Transactional
    public WorkingTimeResponse updateWorkingTime(Jwt jwt, UUID workingTimeId, WorkingTimeUpdateRequest request, UUID xOrgID) {
        // Resolve current membership
        MembershipEntity currentMembership = userService.resolveMembership(jwt, xOrgID);

        // Validate days
        List<DayOfWeek> days = request.getDays();
        if (days == null || days.isEmpty()) {
            throw new WorkingTimeValidationException("At least one day of the week must be selected.");
        }

        // Parse times
        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(request.getStartTime());
            endTime = LocalTime.parse(request.getEndTime());
        } catch (Exception e) {
            throw new WorkingTimeValidationException("Invalid time format. Expected: HH:mm");
        }

        // Validate end time > start time
        if (!endTime.isAfter(startTime)) {
            throw new WorkingTimeValidationException("End time must be after start time.");
        }

        WorkingTimeEntity existingTime = workingTimeRepository.findById(workingTimeId)
                .orElseThrow(() -> new WorkingTimeAccessDeniedException("Working time not found."));

        // Check access: working time must belong to current user and organization
        if (!existingTime.getMembership().getUser().getId().equals(currentMembership.getUser().getId())
                || !existingTime.getOrganizationId().equals(xOrgID)) {
            throw new WorkingTimeAccessDeniedException("No access to this working time.");
        }

        // Check for overlaps (excluding self)
        boolean overlaps = workingTimeRepository.existsOverlappingSlotExcluding(
                currentMembership.getUser().getId(), existingTime.getId(), startTime, endTime, days);

        if (overlaps) {
            throw new WorkingTimeOverlapException("The updated working time overlaps with another existing working time.");
        }

        existingTime.setDays(new HashSet<>(days));
        existingTime.setStartTime(startTime);
        existingTime.setEndTime(endTime);

        WorkingTimeEntity updated = workingTimeRepository.save(existingTime);
        return mapToResponse(updated, false);
    }

    /**
     * Deletes a specific working time slot.
     *
     * @param jwt           The current JWT representing the logged-in user.
     * @param workingTimeId The UUID of the working time slot to delete.
     * @param xOrgID        The UUID of the organization.
     */
    @Transactional
    public void deleteWorkingTime(Jwt jwt, UUID workingTimeId, UUID xOrgID) {
        // Resolve current membership
        MembershipEntity currentMembership = userService.resolveMembership(jwt, xOrgID);

        WorkingTimeEntity existingTime = workingTimeRepository.findById(workingTimeId)
                .orElseThrow(() -> new WorkingTimeAccessDeniedException("Working time not found."));

        // Check access: working time must belong to current user and organization
        if (!existingTime.getMembership().getUser().getId().equals(currentMembership.getUser().getId())
                || !existingTime.getOrganizationId().equals(xOrgID)) {
            throw new WorkingTimeAccessDeniedException("No access to this working time.");
        }

        workingTimeRepository.delete(existingTime);
    }

    /**
     * Retrieves all working time slots for the current user within an organization.
     *
     * @param jwt    The current JWT representing the logged-in user.
     * @param xOrgID The UUID of the organization.
     * @return A {@link WorkingTimeListResponse} containing the list of working times.
     */
    @Transactional(readOnly = true)
    public WorkingTimeListResponse getWorkingTimes(Jwt jwt, UUID xOrgID) {
        // Resolve current membership
        MembershipEntity currentMembership = userService.resolveMembership(jwt, xOrgID);

        // Fetch all working times for this user across all organizations to check for global overlaps
        List<WorkingTimeEntity> allWorkingTimes = workingTimeRepository.findAllByMembershipUserId(currentMembership.getUser().getId());

        // Compute which working times are conflicting
        Set<UUID> conflictingIds = computeConflicting(allWorkingTimes);

        // Map to responses
        List<WorkingTimeResponse> items = allWorkingTimes.stream()
                .filter(wt -> wt.getOrganizationId().equals(xOrgID)) // Only return for current org context
                .map(wt -> mapToResponse(wt, conflictingIds.contains(wt.getId())))
                .toList();

        WorkingTimeListResponse response = new WorkingTimeListResponse();
        response.setItems(items);
        return response;
    }

    private Set<UUID> computeConflicting(List<WorkingTimeEntity> all) {
        Set<UUID> result = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                WorkingTimeEntity a = all.get(i);
                WorkingTimeEntity b = all.get(j);

                boolean timeOverlap = a.getStartTime().isBefore(b.getEndTime())
                        && a.getEndTime().isAfter(b.getStartTime());

                boolean dayOverlap = !Collections.disjoint(a.getDays(), b.getDays());

                if (timeOverlap && dayOverlap) {
                    result.add(a.getId());
                    result.add(b.getId());
                }
            }
        }
        return result;
    }

    private WorkingTimeResponse mapToResponse(WorkingTimeEntity entity, boolean conflicting) {
        WorkingTimeResponse response = new WorkingTimeResponse();
        response.setId(entity.getId());
        response.setAccountId(entity.getMembership().getId());
        response.setOrganizationId(entity.getOrganizationId());
        response.setDays(new ArrayList<>(entity.getDays()));
        response.setStartTime(entity.getStartTime().toString());
        response.setEndTime(entity.getEndTime().toString());
        response.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        response.setConflicting(conflicting);
        return response;
    }
}
