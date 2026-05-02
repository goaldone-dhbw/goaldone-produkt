package de.goaldone.authservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RFC 7807 Problem Detail response DTO.
 * Used for consistent error responses across the API.
 *
 * Structure:
 * - type: URI describing the constraint (e.g., "/constraint/last-admin-violation")
 * - title: Human-readable error title
 * - detail: Specific error context for this instance
 * - status: HTTP status code (409 for constraint violations)
 * - instance: The affected resource URI
 * - timestamp: When the error occurred
 * - violationType: Machine-readable constraint type (LAST_ORG_ADMIN, LAST_SUPER_ADMIN)
 * - suggestion: Optional actionable guidance for resolution
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailDTO {
    private String type;
    private String title;
    private String detail;
    private int status;
    private String instance;
    private LocalDateTime timestamp;
    private String violationType;
    private String suggestion;

    /**
     * Builder convenience method for constraint violations.
     */
    public static class ConstraintViolationBuilder {
        private final ProblemDetailDTOBuilder builder;

        public ConstraintViolationBuilder(String violationType, String title, String detail) {
            this.builder = ProblemDetailDTO.builder()
                    .status(409)
                    .title(title)
                    .detail(detail)
                    .timestamp(LocalDateTime.now())
                    .violationType(violationType);

            if ("LAST_ORG_ADMIN".equals(violationType)) {
                this.builder.type("https://api.goaldone.de/constraint/last-admin-violation");
                this.builder.suggestion("Promote another user to COMPANY_ADMIN before removing this membership.");
            } else if ("LAST_SUPER_ADMIN".equals(violationType)) {
                this.builder.type("https://api.goaldone.de/constraint/last-super-admin-violation");
                this.builder.suggestion("Promote another user to super-admin before removing this status.");
            }
        }

        public ConstraintViolationBuilder instance(String instance) {
            this.builder.instance(instance);
            return this;
        }

        public ConstraintViolationBuilder suggestion(String suggestion) {
            this.builder.suggestion(suggestion);
            return this;
        }

        public ProblemDetailDTO build() {
            return this.builder.build();
        }
    }

    /**
     * Factory method for last-admin constraint violations.
     */
    public static ConstraintViolationBuilder lastAdminViolation(String detail) {
        return new ConstraintViolationBuilder("LAST_ORG_ADMIN", "Last Administrator Cannot Be Removed", detail);
    }

    /**
     * Factory method for last-super-admin constraint violations.
     */
    public static ConstraintViolationBuilder lastSuperAdminViolation(String detail) {
        return new ConstraintViolationBuilder("LAST_SUPER_ADMIN", "Last Super Administrator Cannot Be Removed", detail);
    }
}
