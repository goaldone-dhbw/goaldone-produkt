package de.goaldone.backend.client;

import java.util.List;

/**
 * Represents a Zitadel user grant from the Management v1 API.
 * Since the Zitadel Java SDK has no typed models for Management v1 grants,
 * this record encapsulates the essential grant data returned by the REST API.
 *
 * @param grantId  the unique ID of the grant (used for updates and deletions)
 * @param roleKeys the list of role keys assigned in this grant
 */
public record UserGrantDto(String grantId, List<String> roleKeys) {
}
