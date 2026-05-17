package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkingTimeConflictService {

    private static final EnumSet<DayOfWeek> DEFAULT_WORKING_DAYS = EnumSet.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    );
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_END_TIME = LocalTime.of(17, 0);

    private final UserAccountRepository userAccountRepository;
    private final WorkingTimeRepository workingTimeRepository;

    public boolean hasConflictsForIdentity(UUID userIdentityId) {
        List<UserAccountEntity> accounts = userAccountRepository.findAllByUserIdentityId(userIdentityId);
        List<WorkingTimeEntity> workingTimes =
            workingTimeRepository.findAllByUserAccountUserIdentityId(userIdentityId);

        Map<UUID, List<WorkingTimeEntity>> workingTimesByAccount = workingTimes.stream()
            .collect(Collectors.groupingBy(wt -> wt.getUserAccount().getId()));

        List<EffectiveWorkingTime> effectiveWorkingTimes = accounts.stream()
            .flatMap(account -> effectiveWorkingTimesForAccount(account, workingTimesByAccount).stream())
            .toList();

        for (int i = 0; i < effectiveWorkingTimes.size(); i++) {
            for (int j = i + 1; j < effectiveWorkingTimes.size(); j++) {
                if (effectiveWorkingTimes.get(i).overlaps(effectiveWorkingTimes.get(j))) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<EffectiveWorkingTime> effectiveWorkingTimesForAccount(
        UserAccountEntity account,
        Map<UUID, List<WorkingTimeEntity>> workingTimesByAccount
    ) {
        List<WorkingTimeEntity> accountWorkingTimes =
            workingTimesByAccount.getOrDefault(account.getId(), List.of());

        if (accountWorkingTimes.isEmpty()) {
            return List.of(EffectiveWorkingTime.defaultForAccount());
        }

        return accountWorkingTimes.stream()
            .map(EffectiveWorkingTime::from)
            .toList();
    }

    private record EffectiveWorkingTime(List<DayOfWeek> days, LocalTime startTime, LocalTime endTime) {
        private static EffectiveWorkingTime from(WorkingTimeEntity workingTime) {
            return new EffectiveWorkingTime(
                List.copyOf(workingTime.getDays()),
                workingTime.getStartTime(),
                workingTime.getEndTime()
            );
        }

        private static EffectiveWorkingTime defaultForAccount() {
            return new EffectiveWorkingTime(
                List.copyOf(DEFAULT_WORKING_DAYS),
                DEFAULT_START_TIME,
                DEFAULT_END_TIME
            );
        }

        private boolean overlaps(EffectiveWorkingTime other) {
            return startTime.isBefore(other.endTime)
                && endTime.isAfter(other.startTime)
                && days.stream().anyMatch(other.days::contains);
        }
    }
}
