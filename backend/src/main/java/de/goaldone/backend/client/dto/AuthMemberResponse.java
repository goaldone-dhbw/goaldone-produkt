package de.goaldone.backend.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for member data returned by the auth-service management API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMemberResponse {
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String status;
}
