package de.goaldone.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CompanyRequest", description = "Request body for creating or updating an organization")
public class CompanyRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Organization name", example = "Acme Corporation")
    private String name;

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Schema(description = "Organization unique slug identifier (lowercase alphanumeric and hyphens)", example = "acme-corp")
    private String slug;
}
