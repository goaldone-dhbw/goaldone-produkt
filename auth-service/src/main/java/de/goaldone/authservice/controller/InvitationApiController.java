package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.Invitation;
import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.dto.InvitationStatusResponse;
import de.goaldone.authservice.repository.InvitationRepository;
import de.goaldone.authservice.service.InvitationManagementService;
import de.goaldone.authservice.service.VerificationTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API endpoints for invitation management.
 * These endpoints are publicly accessible (no authentication required, token serves as credential).
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Invitation status and query endpoints")
@Slf4j
public class InvitationApiController {

    private final VerificationTokenService tokenService;
    private final InvitationManagementService invitationService;
    private final InvitationRepository invitationRepository;

    /**
     * Query the status of an invitation by token.
     * Publicly accessible - no authentication required.
     *
     * @param token the invitation token
     * @return InvitationStatusResponse with status details
     */
    @GetMapping("/{token}/status")
    @Operation(summary = "Get invitation status", description = "Queries the status of an invitation using its token. This endpoint is public and does not require authentication.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invitation found and is pending"),
            @ApiResponse(responseCode = "404", description = "Invitation not found"),
            @ApiResponse(responseCode = "409", description = "Invitation has already been accepted or declined"),
            @ApiResponse(responseCode = "410", description = "Invitation has expired")
    })
    public ResponseEntity<InvitationStatusResponse> getInvitationStatus(@PathVariable @Parameter(description = "Invitation token") String token) {
        log.info("Querying invitation status for token");

        // Check token validity
        Optional<String> emailOpt = tokenService.checkToken(token, TokenType.INVITATION);

        if (emailOpt.isEmpty()) {
            log.warn("Invalid or expired token provided");
            // Return 410 Gone for expired tokens
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(InvitationStatusResponse.builder()
                            .token(token)
                            .status("EXPIRED")
                            .build());
        }

        String invitedEmail = emailOpt.get();

        // Find the invitation
        Optional<Invitation> invitationOpt = invitationRepository.findTopByEmailOrderByCreatedAtDesc(invitedEmail);

        if (invitationOpt.isEmpty()) {
            log.warn("No invitation found for email: {}", invitedEmail);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(InvitationStatusResponse.builder()
                            .token(token)
                            .status("NOT_FOUND")
                            .build());
        }

        Invitation invitation = invitationOpt.get();

        // Determine status
        String status;
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            status = "EXPIRED";
            log.info("Invitation expired for email: {}", invitedEmail);
        } else if (invitation.getAcceptanceReason() != null && !invitation.getAcceptanceReason().isEmpty()) {
            status = switch (invitation.getAcceptanceReason()) {
                case "NEW_ACCOUNT", "ACCOUNT_LINKING" -> "ACCEPTED";
                case "DECLINED" -> "DECLINED";
                default -> "UNKNOWN";
            };
            log.info("Invitation status {} for email: {}", status, invitedEmail);
        } else {
            status = "PENDING";
            log.info("Invitation pending for email: {}", invitedEmail);
        }

        // Check for email match
        Optional<UUID> userIdOpt = invitationService.matchInvitedEmailToExistingUser(invitedEmail)
                .map(User::getId);

        InvitationStatusResponse.EmailMatch emailMatch = InvitationStatusResponse.EmailMatch.builder()
                .found(userIdOpt.isPresent())
                .userId(userIdOpt.orElse(null))
                .build();

        InvitationStatusResponse response = InvitationStatusResponse.builder()
                .token(token)
                .status(status)
                .invitedEmail(invitedEmail)
                .organizationId(invitation.getCompany().getId())
                .organizationName(invitation.getCompany().getName())
                .expirationDate(invitation.getExpiresAt())
                .emailMatch(emailMatch)
                .build();

        // Return appropriate HTTP status
        if ("EXPIRED".equals(status)) {
            return ResponseEntity.status(HttpStatus.GONE).body(response);
        } else if ("ACCEPTED".equals(status) || "DECLINED".equals(status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
