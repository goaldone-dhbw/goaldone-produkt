package de.goaldone.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvitationRequest", description = "Request body for creating an invitation")
public class InvitationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Email address to invite", example = "newuser@example.com")
    private String email;

    @NotNull(message = "Company ID is required")
    @Schema(description = "Organization UUID to invite the user to", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID companyId;

    @NotNull(message = "Inviter ID is required")
    @Schema(description = "UUID of the user sending the invitation", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID inviterId;
}
