package de.goaldone.backend.controller;

import de.goaldone.backend.api.AppointmentsApi;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.AppointmentCreate;
import de.goaldone.backend.model.AppointmentListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AppointmentController implements AppointmentsApi {

    @Override
    public ResponseEntity<Appointment> createAppointment(UUID accountId, AppointmentCreate appointmentCreate) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Void> deleteAppointment(UUID accountId, UUID appointmentId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Appointment> getAppointment(UUID accountId, UUID appointmentId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<AppointmentListResponse> listAppointments(UUID accountId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
