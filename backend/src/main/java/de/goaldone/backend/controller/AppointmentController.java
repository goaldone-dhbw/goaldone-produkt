package de.goaldone.backend.controller;

import de.goaldone.backend.api.AppointmentsApi;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.AppointmentCreate;
import de.goaldone.backend.model.AppointmentListResponse;
import de.goaldone.backend.service.AppointmentService;
import de.goaldone.backend.service.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AppointmentController implements AppointmentsApi {

    private final AppointmentService appointmentService;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public ResponseEntity<Appointment> createAppointment(UUID accountId, AppointmentCreate appointmentCreate) {
        Jwt jwt = currentUserResolver.extractJwt();
        Appointment created = appointmentService.createAppointment(accountId, appointmentCreate, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<AppointmentListResponse> listAppointments(UUID accountId) {
        Jwt jwt = currentUserResolver.extractJwt();
        return ResponseEntity.ok(appointmentService.listAppointments(accountId, jwt));
    }

    @Override
    public ResponseEntity<Appointment> getAppointment(UUID accountId, UUID appointmentId) {
        Jwt jwt = currentUserResolver.extractJwt();
        return ResponseEntity.ok(appointmentService.getAppointment(accountId, appointmentId, jwt));
    }

    @Override
    public ResponseEntity<Appointment> updateAppointment(UUID accountId, UUID appointmentId,
                                                         AppointmentCreate appointmentCreate) {
        Jwt jwt = currentUserResolver.extractJwt();
        return ResponseEntity.ok(appointmentService.updateAppointment(accountId, appointmentId, appointmentCreate, jwt));
    }

    @Override
    public ResponseEntity<Void> deleteAppointment(UUID accountId, UUID appointmentId) {
        Jwt jwt = currentUserResolver.extractJwt();
        appointmentService.deleteAppointment(accountId, appointmentId, jwt);
        return ResponseEntity.noContent().build();
    }
}
