package de.goaldone.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;

/**
 * Global exception handler that intercepts exceptions thrown by controllers and maps them to RFC 7807 Problem Details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles LinkTokenExpiredException and returns HTTP 410 (Gone).
     */
    @ExceptionHandler(LinkTokenExpiredException.class)
    public ProblemDetail handleLinkTokenExpired(LinkTokenExpiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/link-token-expired"));
        return pd;
    }

    /**
     * Handles AlreadyLinkedException and returns HTTP 409 (Conflict).
     */
    @ExceptionHandler(AlreadyLinkedException.class)
    public ProblemDetail handleAlreadyLinked(AlreadyLinkedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/already-linked"));
        return pd;
    }

    /**
     * Handles SameOrganizationLinkNotAllowedException and returns HTTP 409 (Conflict).
     */
    @ExceptionHandler(SameOrganizationLinkNotAllowedException.class)
    public ProblemDetail handleSameOrg(SameOrganizationLinkNotAllowedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/same-organization-link-not-allowed"));
        return pd;
    }

    /**
     * Handles NotLinkedException and returns HTTP 400 (Bad Request).
     */
    @ExceptionHandler(NotLinkedException.class)
    public ProblemDetail handleNotLinked(NotLinkedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/not-linked"));
        return pd;
    }

    /**
     * Handles generic ConflictException and returns HTTP 409 (Conflict).
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/conflict"));
        return pd;
    }

    /**
     * Handles ZitadelApiException and returns HTTP 502 (Bad Gateway).
     */
    @ExceptionHandler(ZitadelApiException.class)
    public ProblemDetail handleZitadelApi(ZitadelApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/upstream-error"));
        return pd;
    }

    /**
     * Handles PartialDeletionException and returns HTTP 502 (Bad Gateway) with the list of failed user IDs.
     */
    @ExceptionHandler(PartialDeletionException.class)
    public ProblemDetail handlePartialDeletion(PartialDeletionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "PARTIAL_DELETION_FAILURE");
        pd.setType(URI.create("https://goaldone.de/errors/upstream-error"));
        pd.setProperty("failedUserIds", ex.getFailedUserIds());
        return pd;
    }

    /**
     * Handles WorkingTimeValidationException and returns HTTP 400 (Bad Request).
     */
    @ExceptionHandler(WorkingTimeValidationException.class)
    public ProblemDetail handleWorkingTimeValidation(WorkingTimeValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/working-time-validation"));
        return pd;
    }

    /**
     * Handles WorkingTimeOverlapException and returns HTTP 409 (Conflict).
     */
    @ExceptionHandler(WorkingTimeOverlapException.class)
    public ProblemDetail handleWorkingTimeOverlap(WorkingTimeOverlapException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/working-time-overlap"));
        return pd;
    }

    /**
     * Handles WorkingTimeAccessDeniedException and returns HTTP 403 (Forbidden).
     */
    @ExceptionHandler(WorkingTimeAccessDeniedException.class)
    public ProblemDetail handleWorkingTimeAccessDenied(WorkingTimeAccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/working-time-access-denied"));
        return pd;
    }

    /**
     * Handles ScheduleGenerationException and returns HTTP 500 (Internal Server Error).
     */
    @ExceptionHandler(ScheduleGenerationException.class)
    public ProblemDetail handleScheduleGeneration(ScheduleGenerationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        pd.setType(URI.create("https://goaldone.de/errors/schedule-generation-failed"));
        return pd;
    }

    /**
     * Handles Spring's ResponseStatusException and returns the corresponding status and title.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setTitle(ex.getReason());
        return pd;
    }
}
