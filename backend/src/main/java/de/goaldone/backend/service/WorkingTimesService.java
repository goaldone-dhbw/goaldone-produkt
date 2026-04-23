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
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public WorkingTimeResponse createWorkingTime(UUID currentAccountId, WorkingTimeCreateRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new WorkingTimeValidationException("Endzeit muss nach der Startzeit liegen.");
        }

        UserAccountEntity currentAccount = userAccountRepository.findById(currentAccountId)
            .orElseThrow(() -> new IllegalStateException("Current account not found after authentication"));

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
        entity.setUserAccountId(targetAccount.getId());
        entity.setUserIdentityId(targetAccount.getUserIdentityId());
        entity.setOrganizationId(targetAccount.getOrganizationId());
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setCreatedAt(Instant.now());

        WorkingTimeEntity saved = workingTimeRepository.save(entity);
        return mapToResponse(saved);
    }

    private WorkingTimeResponse mapToResponse(WorkingTimeEntity entity) {
        WorkingTimeResponse response = new WorkingTimeResponse();
        response.setId(entity.getId());
        response.setAccountId(entity.getUserAccountId());
        response.setOrganizationId(entity.getOrganizationId());
        response.setStartTime(OffsetDateTime.ofInstant(entity.getStartTime(), ZoneOffset.UTC));
        response.setEndTime(OffsetDateTime.ofInstant(entity.getEndTime(), ZoneOffset.UTC));
        response.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        return response;
    }
}

