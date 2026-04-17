package de.goaldone.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZitadelUserInfo(
        String email,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName
) {}
