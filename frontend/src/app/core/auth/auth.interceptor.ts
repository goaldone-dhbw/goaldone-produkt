import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { OrgContextService } from '../services/org-context.service';
import { switchMap, catchError, from, throwError } from 'rxjs';

/**
 * Maps an HTTP error response to a user-friendly message string.
 * Returns null if no user-facing message is appropriate for the status code.
 *
 * Exported for testability.
 */
export function mapErrorToUserMessage(error: HttpErrorResponse): string | null {
  if (error.status === 0) {
    // Network error / server unreachable
    return 'Unable to connect to server. Check your connection and try again.';
  }
  switch (error.status) {
    case 403:
      return "You don't have permission to access this resource. Contact your admin.";
    case 410:
      return 'This link has expired. Please request a new one.';
    case 409:
      return 'This action cannot be completed. Please refresh and try again.';
    case 401:
      // Handled separately (logout + redirect) — no user-visible message here
      return null;
    default:
      if (error.status >= 500) {
        return 'Something went wrong. Please try again or contact support.';
      }
      return null; // No user-facing message for other 4xx errors
  }
}

/**
 * HTTP interceptor that handles:
 * 1. Bearer token injection for API requests
 * 2. On-demand token refresh if token is near expiry (D-03, D-14)
 * 3. Conditional X-Org-ID header injection (D-13)
 * 4. HTTP error logging and 401 Unauthorized → logout + redirect
 *
 * Token refresh happens per-request if token expires in less than 5 minutes.
 * X-Org-ID header is included for:
 * - POST, PUT, DELETE requests (always)
 * - GET /organization/members (always)
 * - GET list operations: NOT included (all-org data)
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const orgContextService = inject(OrgContextService);
  const router = inject(Router);

  // Read API base path at runtime to support dynamic env.js injection
  // Default to localhost:8080 for local dev if not set
  const apiBasePath =
    (window as any).__env?.['apiBasePath'] || 'http://localhost:8080';

  // 1. Check if we have a valid access token
  if (!authService.hasValidAccessToken()) {
    // No token at all, let request go through (will get 401)
    return next(req);
  }

  // 2. Check if token will expire soon and refresh if needed
  if (authService.isTokenExpirySoon()) {
    // Refresh token synchronously before continuing with the request
    return from(authService.refreshToken()).pipe(
      switchMap(() => proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next)),
      catchError(() => {
        // Refresh failed, proceed with current token (backend will handle 401)
        return proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next);
      })
    ).pipe(
      catchError((error: HttpErrorResponse) =>
        handleHttpError(error, authService, router)
      )
    ) as any;
  }

  // 3. Token is valid, proceed with authorization and header injection
  return (proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next) as any).pipe(
    catchError((error: HttpErrorResponse) =>
      handleHttpError(error, authService, router)
    )
  );
};

/**
 * Handles HTTP errors from API requests:
 * - 401 Unauthorized: clears session and redirects to login
 * - All others: logs a warning with the mapped user-friendly message
 *
 * Always re-throws the original error so component-level handlers can react.
 */
function handleHttpError(
  error: HttpErrorResponse,
  authService: AuthService,
  router: Router
) {
  if (error.status === 401) {
    console.error(
      '[AuthInterceptor] 401 Unauthorized — clearing session and redirecting to login.',
      { url: error.url }
    );
    authService.logout();
    router.navigateByUrl('/');
  } else {
    const userMessage = mapErrorToUserMessage(error);
    if (userMessage) {
      console.warn('[AuthInterceptor]', userMessage, {
        status: error.status,
        url: error.url,
      });
    } else {
      console.error('[AuthInterceptor] HTTP error:', error.status, error.url, error.message);
    }
  }
  return throwError(() => error);
}

/**
 * Adds Authorization and conditional X-Org-ID headers to the request.
 * @param req Original HTTP request
 * @param authService AuthService for token access
 * @param orgContextService OrgContextService for org context
 * @param apiBasePath API base URL
 * @param next HTTP handler for next interceptor/handler
 * @returns Observable of the handled response
 */
function proceedWithAuthorization(
  req: any,
  authService: AuthService,
  orgContextService: OrgContextService,
  apiBasePath: string,
  next: any
) {
  const token = authService.getAccessToken();

  if (!token || !req.url.startsWith(apiBasePath)) {
    return next(req);
  }

  // Clone request and set Authorization header
  let headers = req.headers.set('Authorization', `Bearer ${token}`);

  // Conditionally add X-Org-ID header based on request method and endpoint
  // D-13: Conditional X-Org-ID Header
  const method = req.method.toUpperCase();
  const isMemberEndpoint = req.url.includes('/organization/members');
  const isDestructive = ['POST', 'PUT', 'DELETE'].includes(method);

  if (isDestructive || isMemberEndpoint) {
    // These requests MUST have X-Org-ID
    const orgId = resolveOrgIdForRequest(orgContextService);
    if (orgId) {
      headers = headers.set('X-Org-ID', orgId);
    }
  }
  // GET (non-member) endpoints: do NOT include X-Org-ID (returns all-org data)

  const clonedReq = req.clone({ headers });
  return next(clonedReq);
}

/**
 * Resolves the organization ID for a request based on context priorities.
 * Priority: dialog org > settings org > default org
 * @param orgContextService OrgContextService
 * @returns Organization ID or null if not available
 */
function resolveOrgIdForRequest(orgContextService: OrgContextService): string | null {
  // Priority: dialog org > settings org > default org
  let orgId = orgContextService.getDialogOrg();
  if (!orgId) {
    orgId = orgContextService.getSettingsOrg();
  }
  if (!orgId) {
    const defaultOrg = orgContextService.getDefaultOrg();
    orgId = defaultOrg?.id || null;
  }
  return orgId;
}

