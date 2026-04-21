package de.goaldone.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleLinkTokenExpired() {
        UUID token = UUID.randomUUID();
        LinkTokenExpiredException ex = new LinkTokenExpiredException(token);

        ProblemDetail result = handler.handleLinkTokenExpired(ex);

        assertEquals(410, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/link-token-expired"), result.getType());
        assertTrue(result.getDetail().contains("Link token"));
        assertTrue(result.getDetail().contains("has expired"));
        assertTrue(result.getProperties() == null || result.getProperties().get("errorCode") == null);
    }

    @Test
    void handleAlreadyLinked() {
        AlreadyLinkedException ex = new AlreadyLinkedException();

        ProblemDetail result = handler.handleAlreadyLinked(ex);

        assertEquals(409, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/already-linked"), result.getType());
        assertNotNull(result.getDetail());
        assertTrue(result.getProperties() == null || result.getProperties().get("errorCode") == null);
    }

    @Test
    void handleSameOrganizationLink() {
        SameOrganizationLinkNotAllowedException ex = new SameOrganizationLinkNotAllowedException();

        ProblemDetail result = handler.handleSameOrg(ex);

        assertEquals(409, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/same-organization-link-not-allowed"), result.getType());
        assertNotNull(result.getDetail());
        assertTrue(result.getProperties() == null || result.getProperties().get("errorCode") == null);
    }

    @Test
    void handleNotLinked() {
        NotLinkedException ex = new NotLinkedException();

        ProblemDetail result = handler.handleNotLinked(ex);

        assertEquals(400, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/not-linked"), result.getType());
        assertNotNull(result.getDetail());
        assertTrue(result.getProperties() == null || result.getProperties().get("errorCode") == null);
    }

    @Test
    void handleLastSuperAdmin() {
        LastSuperAdminException ex = new LastSuperAdminException("Cannot delete last super-admin");

        ProblemDetail result = handler.handleLastSuperAdmin(ex);

        assertEquals(409, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/last-super-admin-cannot-be-deleted"), result.getType());
        assertEquals("Cannot delete last super-admin", result.getDetail());
        assertEquals(LastSuperAdminException.ERROR_CODE, result.getProperties().get("errorCode"));
    }

    @Test
    void handleEmailAlreadyInUse() {
        EmailAlreadyInUseException ex = new EmailAlreadyInUseException("Email is already in use");

        ProblemDetail result = handler.handleEmailAlreadyInUse(ex);

        assertEquals(409, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/email-already-in-use"), result.getType());
        assertEquals("Email is already in use", result.getDetail());
        assertEquals(EmailAlreadyInUseException.ERROR_CODE, result.getProperties().get("errorCode"));
    }

    @Test
    void handleUserNotFound() {
        UserNotFoundException ex = new UserNotFoundException("User not found");

        ProblemDetail result = handler.handleUserNotFound(ex);

        assertEquals(404, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/user-not-found"), result.getType());
        assertEquals("User not found", result.getDetail());
        assertTrue(result.getProperties() == null || result.getProperties().get("errorCode") == null);
    }

    @Test
    void handleZitadelUpstream() {
        ZitadelUpstreamException ex = new ZitadelUpstreamException("Zitadel API error");

        ProblemDetail result = handler.handleZitadelUpstream(ex);

        assertEquals(502, result.getStatus());
        assertEquals(URI.create("https://goaldone.de/errors/zitadel-upstream"), result.getType());
        assertEquals("Zitadel API error", result.getDetail());
        assertTrue(result.getProperties() == null || result.getProperties().get("errorCode") == null);
    }

    // Test exception constructors
    @Test
    void linkTokenExpiredConstructor() {
        UUID token = UUID.randomUUID();
        LinkTokenExpiredException ex = new LinkTokenExpiredException(token);

        assertTrue(ex.getMessage().contains(token.toString()));
    }

    @Test
    void lastSuperAdminExceptionHasErrorCode() {
        assertEquals("LAST_SUPER_ADMIN_CANNOT_BE_DELETED", LastSuperAdminException.ERROR_CODE);
    }

    @Test
    void emailAlreadyInUseExceptionHasErrorCode() {
        assertEquals("EMAIL_ALREADY_IN_USE", EmailAlreadyInUseException.ERROR_CODE);
    }

    @Test
    void userNotFoundExceptionConstructor() {
        UserNotFoundException ex = new UserNotFoundException("Test message");
        assertEquals("Test message", ex.getMessage());
    }

    @Test
    void zitadelUpstreamExceptionConstructor() {
        ZitadelUpstreamException ex = new ZitadelUpstreamException("API error");
        assertEquals("API error", ex.getMessage());
    }

    @Test
    void zitadelUpstreamExceptionWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        ZitadelUpstreamException ex = new ZitadelUpstreamException("Wrapped error", cause);

        assertEquals("Wrapped error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
