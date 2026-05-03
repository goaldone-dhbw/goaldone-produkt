import { HttpInterceptorFn, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { TenantService } from '../services/tenant.service';

/**
 * HTTP context token for overriding the org ID header on a per-request basis.
 * Usage: httpClient.get('/tasks', { context: new HttpContext().set(OVERRIDE_ORG_ID, 'org-uuid') })
 */
export const OVERRIDE_ORG_ID = new HttpContextToken<string>(() => '');

/**
 * Angular functional HTTP interceptor that automatically attaches the X-Org-ID header
 * to all outgoing requests based on the active organization from TenantService.
 *
 * Features:
 * - Extracts active org ID from TenantService.activeOrgId() signal
 * - Attaches to X-Org-ID header on all requests
 * - Supports explicit override via HttpContext.set(OVERRIDE_ORG_ID, 'org-uuid')
 * - Skips header if activeOrgId is null (for unauthenticated requests)
 */
export const tenantInterceptor: HttpInterceptorFn = (req, next) => {
  const tenantService = inject(TenantService);

  // Check for explicit override in HttpContext
  const overrideOrgId = req.context.get(OVERRIDE_ORG_ID);
  const activeOrgId = overrideOrgId || tenantService.getActiveOrgId();

  // Only add header if we have an org ID
  if (activeOrgId) {
    const cloned = req.clone({
      headers: req.headers.set('X-Org-ID', activeOrgId)
    });
    return next(cloned);
  }

  return next(req);
};
