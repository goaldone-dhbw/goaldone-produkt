package de.goaldone.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for the initial Super Admin bootstrapping.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "app.super-admin")
@Validated
public class SuperAdminProperties {

    /**
     * Name of the system organization.
     */
    @NotBlank
    private String orgName = "System Admin";

    /**
     * Slug for the system organization.
     */
    @NotBlank
    private String orgSlug = "system-admin";

    /**
     * Email address of the initial super admin user.
     */
    @NotBlank
    @Email
    private String email;

    /**
     * Initial password for the super admin user.
     */
    @NotBlank
    private String password;

}
