package de.goaldone.backend.controller;

import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScheduleController extends BaseController {

    private final ScheduleService scheduleService;

    public ResponseEntity<ScheduleResponse> generateSchedule(GenerateScheduleRequest generateScheduleRequest) {
        return ResponseEntity.ok(scheduleService.generateSchedule(
                generateScheduleRequest.getGoaldoneUserId(),
                generateScheduleRequest.getAccountIds()
        ));
    }
}
