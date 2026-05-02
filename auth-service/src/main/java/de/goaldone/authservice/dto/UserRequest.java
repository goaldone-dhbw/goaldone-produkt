package de.goaldone.authservice.dto;

import de.goaldone.authservice.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserRequest", description = "Request body for creating or updating a user")
public class UserRequest {

    @Schema(description = "User password (required for creation)", example = "secure-password-123")
    private String password;

    @NotNull
    @Schema(description = "User account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Whether the user has system-wide super-admin privileges", example = "false")
    private boolean superAdmin;

    @NotEmpty
    @Schema(description = "List of email addresses associated with the user (at least one required)")
    private List<EmailRequest> emails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "EmailRequest", description = "Email address information")
    public static class EmailRequest {
        @NotBlank
        @Email
        @Schema(description = "Email address", example = "user@example.com")
        private String email;

        @Schema(description = "Whether this is the primary email for the user", example = "true")
        private boolean primary;

        @Schema(description = "Whether the email address has been verified", example = "false")
        private boolean verified;
    }
}
