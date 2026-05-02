package de.goaldone.authservice.dto;

import de.goaldone.authservice.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserResponse", description = "Response containing user information")
public class UserResponse {

    @Schema(description = "Unique user identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "User account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Whether the user has system-wide super-admin privileges", example = "false")
    private boolean superAdmin;

    @Schema(description = "List of email addresses associated with the user")
    private List<EmailResponse> emails;

    @Schema(description = "Timestamp when the user was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the user was last updated")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "EmailResponse", description = "User email address information")
    public static class EmailResponse {
        @Schema(description = "Unique email record identifier", example = "550e8400-e29b-41d4-a716-446655440001")
        private UUID id;

        @Schema(description = "Email address", example = "user@example.com")
        private String email;

        @Schema(description = "Whether this is the primary email for the user", example = "true")
        private boolean primary;

        @Schema(description = "Whether the email address has been verified", example = "true")
        private boolean verified;
    }
}
