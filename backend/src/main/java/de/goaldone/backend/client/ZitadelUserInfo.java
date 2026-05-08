package de.goaldone.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data record representing user information retrieved from the Zitadel UserInfo endpoint.
 *
 * @param email the user's email address
 * @param givenName the user's first name (given name)
 * @param familyName the user's last name (family name)
 */
public record ZitadelUserInfo(
        String email,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName
) {}
