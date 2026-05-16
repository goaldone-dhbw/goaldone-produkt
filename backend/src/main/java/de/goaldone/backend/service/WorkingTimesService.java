package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.exception.WorkingTimeAccessDeniedException;
import de.goaldone.backend.exception.WorkingTimeOverlapException;
import de.goaldone.backend.exception.WorkingTimeValidationException;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeListResponse;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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

@Service
@RequiredArgsConstructor
public class WorkingTimesService {

    private final UserAccountRepository userAccountRepository;
    private final WorkingTimeRepository workingTimeRepository;

    @Transactional
    public WorkingTimeResponse createWorkingTime(WorkingTimeCreateRequest request) {
        UserAccountEntity currentAccount = resolveCurrentAccount();

        UserAccountEntity targetAccount = userAccountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new WorkingTimeAccessDeniedException(
                "Die angegebene Unternehmenszugehoerigkeit gehoert nicht zum angemeldeten Nutzer."
            ));

        List<DayOfWeek> days = request.getDays();
        if (days == null || days.isEmpty()) {
            throw new WorkingTimeValidationException("Mindestens ein Wochentag muss ausgewählt sein.");
        }

        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(request.getStartTime());
            endTime = LocalTime.parse(request.getEndTime());
        } catch (Exception e) {
            throw new WorkingTimeValidationException("Ungültiges Zeitformat. Erwartet: HH:mm");
        }

        if (!endTime.isAfter(startTime)) {
            throw new WorkingTimeValidationException("Endzeit muss nach der Startzeit liegen.");
        }

        boolean overlaps = workingTimeRepository.existsOverlappingSlot(currentAccount.getUserIdentityId(), startTime, endTime, days);

        if (overlaps) {
            throw new WorkingTimeOverlapException(
                "Die Arbeitszeit überschneidet sich mit einer bestehenden Arbeitszeit des Nutzers."
            );
        }

        WorkingTimeEntity entity = new WorkingTimeEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserAccount(targetAccount);
        entity.setOrganizationId(targetAccount.getOrganizationId());
        entity.setDays(new HashSet<>(days));
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setCreatedAt(Instant.now());

        WorkingTimeEntity saved = workingTimeRepository.save(entity);
        return mapToResponse(saved, false);
    }

    @Transactional
    public WorkingTimeResponse updateWorkingTime(UUID workingTimeId, WorkingTimeUpdateRequest request) {
        UserAccountEntity currentAccount = resolveCurrentAccount();

        List<DayOfWeek> days = request.getDays();
        if (days == null || days.isEmpty()) {
            throw new WorkingTimeValidationException("Mindestens ein Wochentag muss ausgewählt sein.");
        }

        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(request.getStartTime());
            endTime = LocalTime.parse(request.getEndTime());
        } catch (Exception e) {
            throw new WorkingTimeValidationException("Ungültiges Zeitformat. Erwartet: HH:mm");
        }

        if (!endTime.isAfter(startTime)) {
            throw new WorkingTimeValidationException("Endzeit muss nach der Startzeit liegen.");
        }

        WorkingTimeEntity existingTime = workingTimeRepository.findById(workingTimeId)
            .orElseThrow(() -> new WorkingTimeAccessDeniedException("Arbeitszeit nicht gefunden."));

        if (!existingTime.getUserAccount().getUserIdentityId().equals(currentAccount.getUserIdentityId())) {
            throw new WorkingTimeAccessDeniedException("Kein Zugriff auf diese Arbeitszeit.");
        }

        boolean overlaps = workingTimeRepository.existsOverlappingSlotExcluding(currentAccount.getUserIdentityId(), existingTime.getId(), startTime, endTime, days);

        if (overlaps) {
            throw new WorkingTimeOverlapException(
                "Die geänderte Arbeitszeit überschneidet sich mit einer anderen bestehenden Arbeitszeit."
            );
        }

        existingTime.setDays(new HashSet<>(days));
        existingTime.setStartTime(startTime);
        existingTime.setEndTime(endTime);

        WorkingTimeEntity updated = workingTimeRepository.save(existingTime);
        return mapToResponse(updated, false);
    }

    @Transactional
    public void deleteWorkingTime(UUID workingTimeId) {
        UserAccountEntity currentAccount = resolveCurrentAccount();

        WorkingTimeEntity existingTime = workingTimeRepository.findById(workingTimeId)
            .orElseThrow(() -> new WorkingTimeAccessDeniedException("Arbeitszeit nicht gefunden."));

        if (!existingTime.getUserAccount().getUserIdentityId().equals(currentAccount.getUserIdentityId())) {
            throw new WorkingTimeAccessDeniedException("Kein Zugriff auf diese Arbeitszeit.");
        }

        workingTimeRepository.delete(existingTime);
    }

    @Transactional(readOnly = true)
    public WorkingTimeListResponse getWorkingTimes() {
        UserAccountEntity currentAccount = resolveCurrentAccount();

        List<WorkingTimeEntity> allWorkingTimes =
            workingTimeRepository.findAllByUserAccountUserIdentityId(currentAccount.getUserIdentityId());

        Set<UUID> conflictingIds = computeConflicting(allWorkingTimes);

        List<WorkingTimeResponse> items = allWorkingTimes.stream()
            .map(wt -> mapToResponse(wt, conflictingIds.contains(wt.getId())))
            .toList();

        WorkingTimeListResponse response = new WorkingTimeListResponse();
        response.setItems(items);
        return response;
    }

    private UserAccountEntity resolveCurrentAccount() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userAccountRepository.findByZitadelSub(jwt.getSubject())
            .orElseThrow(() -> new WorkingTimeAccessDeniedException("Account not found"));
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
        response.setAccountId(entity.getUserAccount().getId());
        response.setOrganizationId(entity.getOrganizationId());
        response.setDays(new ArrayList<>(entity.getDays()));
        response.setStartTime(entity.getStartTime().toString());
        response.setEndTime(entity.getEndTime().toString());
        response.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        response.setConflicting(conflicting);
        return response;
    }
}
