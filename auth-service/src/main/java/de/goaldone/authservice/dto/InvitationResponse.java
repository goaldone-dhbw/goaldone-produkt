package de.goaldone.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvitationResponse", description = "Response containing invitation information")
public class InvitationResponse {

    @Schema(description = "Unique invitation identifier", example = "550e8400-e29b-41d4-a716-446655440003")
    private UUID id;

    @Schema(description = "Email address invited", example = "newuser@example.com")
    private String email;

    @Schema(description = "Organization UUID", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID companyId;

    @Schema(description = "UUID of the user who sent the invitation", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID inviterId;

    @Schema(description = "Timestamp when the invitation expires")
    private LocalDateTime expiresAt;

    @Schema(description = "Timestamp when the invitation was created")
    private LocalDateTime createdAt;
}
