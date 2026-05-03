import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { AuthService } from './auth.service';
import { OrgContextService } from '../services/org-context.service';
import { switchMap, catchError, from, of } from 'rxjs';

/**
 * HTTP interceptor that handles:
 * 1. Bearer token injection for API requests
 * 2. On-demand token refresh if token is near expiry (D-03, D-14)
 * 3. Conditional X-Org-ID header injection (D-13)
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
    ) as any;
  }

  // 3. Token is valid, proceed with authorization and header injection
  return proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next);
};

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
