package de.goaldone.backend.scheduler.types.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.entity.ScheduleEntryEntity;
import de.goaldone.backend.entity.SchedulePlanEntity;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.model.UnscheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ScheduleMapper {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectMapper objectMapper;

    public ScheduleMapper() {
        this.objectMapper = new ObjectMapper();
    }

    public ScheduleResponse mapToScheduleResult(UUID accountId, SchedulingContext context, SchedulingResult schedule) {
        return mapToScheduleResultWithIds(accountId, context, schedule, Map.of());
    }

    public ScheduleResponse mapToScheduleResultWithIds(UUID accountId, SchedulingContext context,
                                                       SchedulingResult schedule, Map<ScheduledChunk, UUID> chunkIdMap) {
        ScheduleResponse response = new ScheduleResponse();
        response.generatedAt(OffsetDateTime.now());
        response.accountId(accountId);
        response.from(context.fromDate().toLocalDate());
        response.score(schedule.score());
        response.setWarnings(schedule.scheduleWarnings());
        response.to(getToDate(schedule.schedule(), context.fromDate().toLocalDate()));
        response.totalWorkMinutes(getWorkMinutes(schedule.schedule()));
        response.entries(getScheduleWithIds(schedule.schedule(), chunkIdMap));
        response.unscheduledTasks(schedule.schedule().unscheduledTasks());
        response.appointments(context.allAppointments());
        return response;
    }

    public ScheduleResponse mapPlanToResponse(SchedulePlanEntity plan, List<ScheduleEntryEntity> entries,
                                              List<Appointment> appointments) {
        ScheduleResponse response = new ScheduleResponse();
        response.setAccountId(plan.getAccountId());
        response.setGeneratedAt(OffsetDateTime.ofInstant(plan.getGeneratedAt(), ZoneOffset.UTC));
        response.setFrom(plan.getFromDate());
        response.setTo(plan.getToDate());
        response.setScore(plan.getScore());
        response.setTotalWorkMinutes(plan.getTotalWorkMinutes());
        response.setWarnings(deserializeWarnings(plan.getWarningsJson()));
        response.setUnscheduledTasks(deserializeUnscheduledTasks(plan.getUnscheduledTasksJson()));
        response.setEntries(entries.stream().map(this::mapEntryEntityToDto).toList());
        response.setAppointments(appointments != null ? appointments : List.of());
        return response;
    }

    public ScheduleEntry mapEntryEntityToDto(ScheduleEntryEntity entity) {
        return new ScheduleEntry()
                .entryId(entity.getId())
                .source(ScheduleEntry.SourceEnum.ONE_TIME)
                .occurrenceDate(entity.getOccurrenceDate())
                .startTime(entity.getStartAt().toLocalTime().format(TIME_FORMATTER))
                .endTime(entity.getEndAt().toLocalTime().format(TIME_FORMATTER))
                .type("TASK".equals(entity.getEntryType())
                        ? ScheduleEntry.TypeEnum.TASK
                        : ScheduleEntry.TypeEnum.APPOINTMENT)
                .isBreak(entity.getIsBreak())
                .isCompleted(entity.getIsCompleted())
                .originalItemId(entity.getOriginalItemId())
                .originalItemTitle(entity.getOriginalItemTitle())
                .chunkIndex(entity.getChunkIndex())
                .totalChunks(entity.getTotalChunks());
    }

    private Integer getWorkMinutes(SolverState schedule) {
        return schedule.scheduledChunks().stream()
                .mapToInt(c -> c.slot().durationMinutes())
                .sum();
    }

    /**
     * @param schedule The generated schedule
     * @param fromDate The date from which on the plan is generated
     * @return The date of the latest scheduled task
     */
    private LocalDate getToDate(SolverState schedule, LocalDate fromDate) {
        Optional<LocalDate> latestDate = schedule.scheduledChunks().stream()
                .map(c -> c.slot().date())
                .max(LocalDate::compareTo);
        return latestDate.orElse(fromDate);
    }

    /**
     * @param state The generated schedule
     * @return ScheduledChunks mapped as List of ScheduleEntry
     */
    private List<ScheduleEntry> getScheduleWithIds(SolverState state, Map<ScheduledChunk, UUID> chunkIdMap) {
        List<ScheduleEntry> schedule = new ArrayList<>();
        for (ScheduledChunk scheduledChunk : state.scheduledChunks()) {
            UUID entryId = chunkIdMap.get(scheduledChunk);
            schedule.add(toScheduleEntry(scheduledChunk, entryId));
        }
        return schedule;
    }

    /**
     * @param scheduledChunk A single scheduled chunk
     * @return A ScheduledChunk mapped as ScheduleEntry
     */
    private ScheduleEntry toScheduleEntry(ScheduledChunk scheduledChunk, UUID entryId) {
        ScheduleEntry entry = new ScheduleEntry()
                .originalItemTitle(scheduledChunk.chunk().taskTitle())
                .source(ScheduleEntry.SourceEnum.ONE_TIME)
                .occurrenceDate(scheduledChunk.date())
                .startTime(scheduledChunk.startTime().format(TIME_FORMATTER))
                .endTime(scheduledChunk.endTime().format(TIME_FORMATTER))
                .type(ScheduleEntry.TypeEnum.TASK)
                .isBreak(false)
                .isCompleted(false)
                .originalItemId(scheduledChunk.chunk().taskId())
                .chunkIndex(scheduledChunk.chunk().chunkIndex())
                .totalChunks(scheduledChunk.chunk().totalChunks());
        if (entryId != null) {
            entry.entryId(entryId);
        }
        return entry;
    }

    private List<ScheduleWarning> deserializeWarnings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ScheduleWarning>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize warnings JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private List<UnscheduledTask> deserializeUnscheduledTasks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UnscheduledTask>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize unscheduled tasks JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
