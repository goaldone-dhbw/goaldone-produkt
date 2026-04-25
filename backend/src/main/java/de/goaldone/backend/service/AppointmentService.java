package de.goaldone.backend.service;

import de.goaldone.backend.model.AppointmentListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {
    public AppointmentListResponse listAppointments(UUID accountId) {
        return null; //TODO
    }
    // TODO: Implement appointment service methods
}
