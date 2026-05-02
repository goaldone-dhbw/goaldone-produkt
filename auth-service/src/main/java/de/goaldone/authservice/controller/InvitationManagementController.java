package de.goaldone.authservice.controller;

import de.goaldone.authservice.dto.InvitationRequest;
import de.goaldone.authservice.dto.InvitationResponse;
import de.goaldone.authservice.service.InvitationManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Invitation management endpoints")
public class InvitationManagementController {

    private final InvitationManagementService invitationService;

    @PostMapping
    @Operation(summary = "Create a new invitation", description = "Creates a new invitation for a user to join an organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invitation created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Invitation already exists")
    })
    public ResponseEntity<InvitationResponse> createInvitation(@Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.createInvitation(request));
    }

    @GetMapping("/{token}")
    @Operation(summary = "Get invitation by token", description = "Retrieves invitation details by its unique token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invitation found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Invitation not found"),
            @ApiResponse(responseCode = "410", description = "Invitation has expired")
    })
    public ResponseEntity<InvitationResponse> getInvitationByToken(@PathVariable @Parameter(description = "Invitation token") UUID token) {
        return ResponseEntity.ok(invitationService.getInvitationByToken(token));
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel invitation", description = "Cancels an existing invitation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invitation cancelled successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public void cancelInvitation(@PathVariable @Parameter(description = "Invitation token") UUID token) {
        invitationService.cancelInvitation(token);
    }
}
