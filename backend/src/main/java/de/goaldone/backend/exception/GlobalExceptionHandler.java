package de.goaldone.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LinkTokenExpiredException.class)
    public ProblemDetail handleLinkTokenExpired(LinkTokenExpiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/link-token-expired"));
        return pd;
    }

    @ExceptionHandler(AlreadyLinkedException.class)
    public ProblemDetail handleAlreadyLinked(AlreadyLinkedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/already-linked"));
        return pd;
    }

    @ExceptionHandler(SameOrganizationLinkNotAllowedException.class)
    public ProblemDetail handleSameOrg(SameOrganizationLinkNotAllowedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/same-organization-link-not-allowed"));
        return pd;
    }

    @ExceptionHandler(NotLinkedException.class)
    public ProblemDetail handleNotLinked(NotLinkedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/not-linked"));
        return pd;
    }

    @ExceptionHandler(LastSuperAdminException.class)
    public ProblemDetail handleLastSuperAdmin(LastSuperAdminException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/last-super-admin-cannot-be-deleted"));
        pd.setProperty("errorCode", LastSuperAdminException.ERROR_CODE);
        return pd;
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/email-already-in-use"));
        pd.setProperty("errorCode", EmailAlreadyInUseException.ERROR_CODE);
        return pd;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/user-not-found"));
        return pd;
    }

    @ExceptionHandler(ZitadelUpstreamException.class)
    public ProblemDetail handleZitadelUpstream(ZitadelUpstreamException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/zitadel-upstream"));
        return pd;
    }
}
