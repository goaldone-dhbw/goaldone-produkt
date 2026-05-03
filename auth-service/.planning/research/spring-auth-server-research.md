# Research: Spring Authorization Server 1.3+ & Multi-Tenancy

## New Features in Spring AS 1.3
- **Built-in Multitenancy:** Support for multiple issuers per host using path-based identifiers (e.g., `/tenant-a`, `/tenant-b`).
- **Token Exchange (RFC 8693):** Support for `token-exchange` grant type, allowing services to exchange user tokens for downstream service tokens.
- **mTLS Client Authentication:** Support for certificate-bound access tokens (`cnf` claim).
- **Dynamic Tenant Management:** Pattern for adding/removing tenants and keys at runtime.

## Best Practices for "Multi-Org" (as per Goaldone requirements)
The Goaldone project follows a "Shared User, Multiple Organizations" model. This is slightly different from strict OIDC multi-tenancy where each tenant is isolated.

### 1. Token Strategy
- **Shared Issuer:** Continue using a single issuer if users are global across the ecosystem.
- **Custom Claims:** The `orgs` claim proposed in `plan.md` is standard for this model.
- **Audience (aud):** Ensure the `aud` claim includes the `resource-server` to prevent token misuse.

### 2. Multi-Email Linking
- **Verification:** Always verify secondary emails before linking.
- **Primary Email:** Maintain a `is_primary` flag for the user's main identity.
- **OIDC link_account flow:** Use a specialized flow where the user authenticates with the second email to prove ownership, then merges the identity in the DB.

### 3. Invitation Flow
- **Token Security:** Use `SecureRandom` for high entropy tokens.
- **State Management:** Use a `status` field (PENDING, USED, EXPIRED) to prevent reuse.
- **Account Discovery:** When an invitation is sent to an existing email, provide a flow to "Link existing account" instead of "Set password".

### 4. Security
- **PKCE:** Mandatory for all Authorization Code flows (OAuth 2.1 requirement).
- **Session Invalidation:** On password reset, revoke all active `oauth2_authorizations`.
- **JWKS Persistence:** Store RSA keys in a persistent store (DB or Vault) to ensure tokens survive restarts.

## Architectural Decision
We will stick with the **Single Issuer + Custom Claims** model as specified in `plan.md`, as it best supports the "one user, many orgs" requirement. However, we will leverage Spring AS 1.3's improved flexibility for dynamic client registration and token customization.
