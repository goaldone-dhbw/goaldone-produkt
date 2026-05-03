package de.goaldone.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MemberListItemResponse", description = "Member information for organization member list")
public class MemberListItemResponse {

    @Schema(description = "User UUID")
    private UUID userId;

    @Schema(description = "Email address")
    private String email;

    @Schema(description = "First name")
    private String firstName;

    @Schema(description = "Last name")
    private String lastName;

    @Schema(description = "Role in organization")
    private String role;

    @Schema(description = "Membership status (ACTIVE or INVITED)")
    private String status;
}
