package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.exception.WorkingTimeAccessDeniedException;
import de.goaldone.backend.exception.WorkingTimeOverlapException;
import de.goaldone.backend.exception.WorkingTimeValidationException;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkingTimesService {

    private final UserAccountRepository userAccountRepository;
    private final WorkingTimeRepository workingTimeRepository;
    private final UserIdentityService userIdentityService;

    @Transactional
    public WorkingTimeResponse createWorkingTime(Jwt jwt, WorkingTimeCreateRequest request) {
        if(!userIdentityService.hasUserAccessToAccount(jwt, request.getAccountId())) {
            throw new WorkingTimeAccessDeniedException("Der angemeldete Nutzer hat keinen Zugriff auf diesen Account.");
        }

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new WorkingTimeValidationException("Endzeit muss nach der Startzeit liegen.");
        }

        UserAccountEntity targetAccount = userAccountRepository.findByIdAndUserIdentityId(
                request.getAccountId(),
                currentAccount.getUserIdentityId()
            )
            .orElseThrow(() -> new WorkingTimeAccessDeniedException(
                "Die angegebene Unternehmenszugehoerigkeit gehoert nicht zum angemeldeten Nutzer."
            ));

        Instant startTime = request.getStartTime().toInstant();
        Instant endTime = request.getEndTime().toInstant();

        boolean overlaps = workingTimeRepository.existsOverlappingSlot(
            currentAccount.getUserIdentityId(),
            startTime,
            endTime
        );

        if (overlaps) {
            throw new WorkingTimeOverlapException(
                "Die Arbeitszeit ueberschneidet sich mit einer bestehenden Arbeitszeit des Nutzers."
            );
        }

        WorkingTimeEntity entity = new WorkingTimeEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserAccount(targetAccount);
        entity.setUserIdentityId(targetAccount.getUserIdentityId());
        entity.setOrganizationId(targetAccount.getOrganizationId());
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setCreatedAt(Instant.now());

        WorkingTimeEntity saved = workingTimeRepository.save(entity);
        return mapToResponse(saved);
    }

    @Transactional
    public WorkingTimeResponse updateWorkingTime(UUID currentAccountId, UUID workingTimeId, WorkingTimeUpdateRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new WorkingTimeValidationException("Endzeit muss nach der Startzeit liegen.");
        }

        UserAccountEntity currentAccount = userAccountRepository.findById(currentAccountId)
                .orElseThrow(() -> new IllegalStateException("Current account not found"));

        WorkingTimeEntity existingTime = workingTimeRepository.findById(workingTimeId)
                .orElseThrow(() -> new WorkingTimeAccessDeniedException("Arbeitszeit nicht gefunden."));

        if (!existingTime.getUserIdentityId().equals(currentAccount.getUserIdentityId())) {
            throw new WorkingTimeAccessDeniedException("Kein Zugriff auf diese Arbeitszeit.");
        }

        Instant newStartTime = request.getStartTime().toInstant();
        Instant newEndTime = request.getEndTime().toInstant();

        boolean overlaps = workingTimeRepository.existsOverlappingSlotExcluding(
                currentAccount.getUserIdentityId(),
                existingTime.getId(),
                newStartTime,
                newEndTime
        );

        if (overlaps) {
            throw new WorkingTimeOverlapException(
                    "Die geänderte Arbeitszeit überschneidet sich mit einer anderen bestehenden Arbeitszeit."
            );
        }

        existingTime.setStartTime(newStartTime);
        existingTime.setEndTime(newEndTime);

        WorkingTimeEntity updated = workingTimeRepository.save(existingTime);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteWorkingTime(UUID currentAccountId, UUID workingTimeId) {
        UserAccountEntity currentAccount = userAccountRepository.findById(currentAccountId)
                .orElseThrow(() -> new IllegalStateException("Current account not found"));

        WorkingTimeEntity existingTime = workingTimeRepository.findById(workingTimeId)
                .orElseThrow(() -> new WorkingTimeAccessDeniedException("Arbeitszeit nicht gefunden."));

        if (!existingTime.getUserIdentityId().equals(currentAccount.getUserIdentityId())) {
            throw new WorkingTimeAccessDeniedException("Kein Zugriff auf diese Arbeitszeit.");
        }

        workingTimeRepository.delete(existingTime);
    }

    private WorkingTimeResponse mapToResponse(WorkingTimeEntity entity) {
        WorkingTimeResponse response = new WorkingTimeResponse();
        response.setId(entity.getId());
        response.setAccountId(entity.getUserAccount().getId());
        response.setOrganizationId(entity.getOrganizationId());
        response.setStartTime(OffsetDateTime.ofInstant(entity.getStartTime(), ZoneOffset.UTC));
        response.setEndTime(OffsetDateTime.ofInstant(entity.getEndTime(), ZoneOffset.UTC));
        response.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        return response;
    }
}

