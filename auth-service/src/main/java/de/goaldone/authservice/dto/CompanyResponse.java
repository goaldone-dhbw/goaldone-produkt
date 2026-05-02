package de.goaldone.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CompanyResponse", description = "Response containing organization information")
public class CompanyResponse {

    @Schema(description = "Unique organization identifier", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID id;

    @Schema(description = "Organization name", example = "Acme Corporation")
    private String name;

    @Schema(description = "Organization unique slug identifier", example = "acme-corp")
    private String slug;

    @Schema(description = "Timestamp when the organization was created")
    private LocalDateTime createdAt;
}
