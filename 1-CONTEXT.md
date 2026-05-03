# 1-CONTEXT.md - Phase 1: Auth-Service Hardening

## Decision Summary

| Area | Decision | Rationale |
|------|----------|-----------|
| **Multi-Org JWT Strategy** | **Option B:** JWT includes all memberships in the `orgs` claim. | Allows the frontend to act across multiple organizations without re-login. Tenant isolation is enforced via URL/body parameters (e.g., `accountId`, `orgId`) as defined in `api-spec/openapi.yaml`. |
| **RSA Key Persistence** | **Filesystem + ENV:** Keys stored in `/keys/signing-key.pem` by default. | Works consistently across Docker Compose (volume mount) and local development. Path must be overridable via `APP_KEY_PATH` environment variable. |
| **Frontend Client Registration** | **Configurable via ENV:** Client details are loaded from environment variables. | Decouples code from environment-specific IDs/URIs. Default fallback to `goaldone-web` and `http://localhost:4200/callback`. |

## Multi-Org Token Contract
The `auth-service` JWT must include the following claims for every authenticated user:
- `sub`: The internal UUID of the user.
- `user_id`: Same as `sub` (stringified UUID).
- `primary_email`: The primary verified email address.
- `emails`: An array of all verified email addresses for the user.
- `orgs`: An array of objects:
  ```json
  [
    { "id": "uuid", "slug": "company-slug", "role": "USER | COMPANY_ADMIN" }
  ]
  ```
- `super_admin`: Boolean flag indicating global system administrator status.
- `authorities`: A flat list of strings representing all permissions (e.g., `ROLE_USER`, `ROLE_COMPANY_ADMIN`).

## RSA Key Strategy
- **Persistence:** On startup, the `auth-service` must check for an existing RSA key pair at `APP_KEY_PATH`. If not found, it generates a new pair and saves it.
- **Stable `kid`:** The Key ID (`kid`) in the JWKS must remain stable across restarts if the same key pair is used.
- **Docker Integration:** The `docker-compose.yaml` (Phase 1 execution) must mount a volume to the key path to ensure persistence across container recreation.

## Client Configuration
The following environment variables must be supported in `auth-service`:
- `FRONTEND_CLIENT_ID`: The OAuth2 client ID for the Angular app (default: `goaldone-web`).
- `FRONTEND_CLIENT_SECRET`: Optional secret (not needed for PKCE but supported for completeness).
- `FRONTEND_REDIRECT_URIS`: Comma-separated list of allowed redirect URIs.
- `FRONTEND_POST_LOGOUT_REDIRECT_URIS`: Comma-separated list of allowed logout redirect URIs.

## Implementation Guardrails
- **No In-Memory Keys:** `generateRsaKey()` in `AuthorizationServerConfig` must be refactored to check the filesystem.
- **OpenAPI Alignment:** Every backend request validation must verify that the `accountId` or `orgId` in the path matches one of the `id`s in the `orgs` claim of the JWT.
- **JWKS Cacheability:** Ensure standard HTTP caching headers are present on the `/oauth2/jwks` endpoint.

## Next Steps for Downstream Agents
1. **Researcher:** Focus on identifying the exact location in `auth-service` where the `RegisteredClientRepository` and `JWKSource` are instantiated to apply these changes.
2. **Planner:** Create tasks for refactoring the configuration classes, adding filesystem key management, and updating the initialization logic to use environment variables.
