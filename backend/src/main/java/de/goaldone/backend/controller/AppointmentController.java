package de.goaldone.backend.controller;

import de.goaldone.backend.api.AppointmentsApi;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.AppointmentCreate;
import de.goaldone.backend.model.AppointmentListResponse;
import de.goaldone.backend.security.AuthorizationFacade;
import de.goaldone.backend.service.AppointmentService;
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

    private final AppointmentService appointmentService;
    private final AuthorizationFacade authorizationFacade;

    @Override
    public ResponseEntity<Appointment> createAppointment(UUID accountId, AppointmentCreate appointmentCreate) {
        authorizationFacade.requireAccountAccess(accountId);
        Appointment created = appointmentService.createAppointment(accountId, appointmentCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<AppointmentListResponse> listAppointments(UUID accountId) {
        authorizationFacade.requireAccountAccess(accountId);
        return ResponseEntity.ok(appointmentService.listAppointments(accountId));
    }

    @Override
    public ResponseEntity<Appointment> getAppointment(UUID accountId, UUID appointmentId) {
        authorizationFacade.requireAccountAccess(accountId);
        return ResponseEntity.ok(appointmentService.getAppointment(accountId, appointmentId));
    }

    @Override
    public ResponseEntity<Appointment> updateAppointment(UUID accountId, UUID appointmentId,
                                                         AppointmentCreate appointmentCreate) {
        authorizationFacade.requireAccountAccess(accountId);
        return ResponseEntity.ok(appointmentService.updateAppointment(accountId, appointmentId, appointmentCreate));
    }

    @Override
    public ResponseEntity<Void> deleteAppointment(UUID accountId, UUID appointmentId) {
        authorizationFacade.requireAccountAccess(accountId);
        appointmentService.deleteAppointment(accountId, appointmentId);
        return ResponseEntity.noContent().build();
    }
}
