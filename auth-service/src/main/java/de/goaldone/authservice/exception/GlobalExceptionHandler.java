package de.goaldone.authservice.exception;

import de.goaldone.authservice.dto.ProblemDetailDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Global exception handler that returns RFC 7807 Problem Details.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Entity Not Found");
        problemDetail.setType(URI.create("https://api.goaldone.de/errors/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.goaldone.de/errors/bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Handle invitation token expired exceptions.
     * Returns 410 Gone with RFC 7807 Problem Detail.
     */
    @ExceptionHandler(InvitationTokenExpiredException.class)
    public ResponseEntity<ProblemDetailDTO> handleInvitationTokenExpiredException(InvitationTokenExpiredException ex) {
        log.warn("Invitation token expired: {}", ex.getMessage());

        ProblemDetailDTO detail = ProblemDetailDTO.builder()
                .status(410)
                .title("Invitation Token Expired")
                .detail(ex.getMessage())
                .type("https://api.goaldone.de/errors/invitation-expired")
                .suggestion("Request new invitation from organization admin")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.GONE)
                .header("Content-Type", "application/problem+json")
                .body(detail);
    }

    /**
     * Handle invitation invalid token exceptions.
     * Returns 404 Not Found with RFC 7807 Problem Detail.
     */
    @ExceptionHandler(InvitationInvalidTokenException.class)
    public ResponseEntity<ProblemDetailDTO> handleInvitationInvalidTokenException(InvitationInvalidTokenException ex) {
        log.warn("Invalid invitation token: {}", ex.getMessage());

        ProblemDetailDTO detail = ProblemDetailDTO.builder()
                .status(404)
                .title("Invitation Token Not Found")
                .detail(ex.getMessage())
                .type("https://api.goaldone.de/errors/invitation-invalid")
                .suggestion("Verify token format and request new invitation if needed")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/problem+json")
                .body(detail);
    }

    /**
     * Handle invitation already accepted exceptions.
     * Returns 409 Conflict with RFC 7807 Problem Detail.
     */
    @ExceptionHandler(InvitationAlreadyAcceptedException.class)
    public ResponseEntity<ProblemDetailDTO> handleInvitationAlreadyAcceptedException(InvitationAlreadyAcceptedException ex) {
        log.warn("Invitation already accepted: {}", ex.getMessage());

        ProblemDetailDTO detail = ProblemDetailDTO.builder()
                .status(409)
                .title("Invitation Already Accepted")
                .detail(ex.getMessage())
                .type("https://api.goaldone.de/errors/invitation-already-accepted")
                .suggestion("This invitation has already been accepted. Log in to access your account.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("Content-Type", "application/problem+json")
                .body(detail);
    }

    /**
     * Handle invitation already declined exceptions.
     * Returns 409 Conflict with RFC 7807 Problem Detail.
     */
    @ExceptionHandler(InvitationAlreadyDeclinedException.class)
    public ResponseEntity<ProblemDetailDTO> handleInvitationAlreadyDeclinedException(InvitationAlreadyDeclinedException ex) {
        log.warn("Invitation already declined: {}", ex.getMessage());

        ProblemDetailDTO detail = ProblemDetailDTO.builder()
                .status(409)
                .title("Invitation Already Declined")
                .detail(ex.getMessage())
                .type("https://api.goaldone.de/errors/invitation-already-declined")
                .suggestion("Request new invitation from organization admin")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("Content-Type", "application/problem+json")
                .body(detail);
    }

    /**
     * Handle generic invitation flow exceptions.
     * Returns 400 Bad Request with RFC 7807 Problem Detail.
     */
    @ExceptionHandler(InvitationFlowException.class)
    public ResponseEntity<ProblemDetailDTO> handleInvitationFlowException(InvitationFlowException ex) {
        log.error("Invitation flow error: {}", ex.getMessage(), ex);

        ProblemDetailDTO detail = ProblemDetailDTO.builder()
                .status(400)
                .title("Invitation Flow Error")
                .detail(ex.getMessage())
                .type("https://api.goaldone.de/errors/invitation-flow-error")
                .suggestion("Please try again or contact support if the problem persists")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/problem+json")
                .body(detail);
    }

    /**
     * Handle business constraint violations (last-admin/last-super-admin).
     * Returns 409 Conflict with RFC 7807 Problem Detail including violation type and actionable suggestion.
     */
    @ExceptionHandler(LastAdminViolationException.class)
    public ResponseEntity<ProblemDetailDTO> handleLastAdminViolationException(LastAdminViolationException ex) {
        log.warn("Business constraint violation: {} for user {} in organization {}",
                ex.getViolationType(), ex.getAffectedUserId(), ex.getOrganizationId());

        ProblemDetailDTO detail;
        if ("LAST_ORG_ADMIN".equals(ex.getViolationType())) {
            detail = ProblemDetailDTO.lastAdminViolation(ex.getMessage())
                    .build();
        } else if ("LAST_SUPER_ADMIN".equals(ex.getViolationType())) {
            detail = ProblemDetailDTO.lastSuperAdminViolation(ex.getMessage())
                    .build();
        } else {
            detail = ProblemDetailDTO.builder()
                    .status(409)
                    .title("Business Constraint Violation")
                    .detail(ex.getMessage())
                    .violationType(ex.getViolationType())
                    .timestamp(LocalDateTime.now())
                    .type("https://api.goaldone.de/constraint/business-violation")
                    .build();
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("Content-Type", "application/problem+json")
                .body(detail);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.goaldone.de/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        // Do not expose internal details in production, but helpful for debugging in local/test
        return problemDetail;
    }
}
