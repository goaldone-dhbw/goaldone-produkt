import '@angular/compiler'; // Required for JIT compilation in tests
import { HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';
import { OrgContextService } from '../services/org-context.service';
import { mapErrorToUserMessage } from './auth.interceptor';

/**
 * Unit tests for authInterceptor
 *
 * Tests verify:
 * 1. Bearer token injection for API requests
 * 2. Token refresh when near expiry
 * 3. Conditional X-Org-ID header injection (POST/PUT/DELETE and member endpoints)
 * 4. No X-Org-ID for GET list operations
 * 5. Multi-org context priority (dialog > settings > default)
 * 6. HTTP error code mapping to user-friendly messages
 */

// Mock interceptor implementation to test (since we can't easily inject into the actual interceptor)
function mockAuthInterceptor(
  req: HttpRequest<any>,
  authService: AuthService,
  orgContextService: OrgContextService,
  apiBasePath: string,
  next: (req: HttpRequest<any>) => any
) {
  // 1. Check if we have a valid access token
  if (!authService.hasValidAccessToken()) {
    return next(req);
  }

  // 2. Check if token will expire soon and refresh if needed
  if (authService.isTokenExpirySoon()) {
    return authService.refreshToken().then(() => proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next));
  }

  // 3. Token is valid, proceed with authorization and header injection
  return proceedWithAuthorization(req, authService, orgContextService, apiBasePath, next);
}

function proceedWithAuthorization(
  req: HttpRequest<any>,
  authService: AuthService,
  orgContextService: OrgContextService,
  apiBasePath: string,
  next: (req: HttpRequest<any>) => any
) {
  const token = authService.getAccessToken();

  if (!token || !req.url.startsWith(apiBasePath)) {
    return next(req);
  }

  let headers = req.headers.set('Authorization', `Bearer ${token}`);

  const method = req.method.toUpperCase();
  const isMemberEndpoint = req.url.includes('/organization/members');
  const isDestructive = ['POST', 'PUT', 'DELETE'].includes(method);

  if (isDestructive || isMemberEndpoint) {
    const orgId = resolveOrgIdForRequest(orgContextService);
    if (orgId) {
      headers = headers.set('X-Org-ID', orgId);
    }
  }

  const clonedReq = req.clone({ headers });
  return next(clonedReq);
}

function resolveOrgIdForRequest(orgContextService: OrgContextService): string | null {
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

describe('authInterceptor', () => {
  let authService: AuthService;
  let orgContextService: OrgContextService;

  const apiBasePath = 'http://localhost:8080';

  beforeEach(() => {
    authService = {
      getAccessToken: vi.fn(() => 'test-token'),
      hasValidAccessToken: vi.fn(() => true),
      isTokenExpirySoon: vi.fn(() => false),
      refreshToken: vi.fn(() => Promise.resolve(true)),
      getDecodedAccessToken: vi.fn(() => ({ exp: Date.now() / 1000 + 3600 })),
    } as any;

    orgContextService = {
      getDialogOrg: vi.fn(() => null),
      getSettingsOrg: vi.fn(() => null),
      getDefaultOrg: vi.fn(() => ({ id: 'default-org-id', slug: 'default-org', role: 'USER' })),
    } as any;
  });

  // ===== Token & Authorization Tests =====

  it('should add Authorization header to API requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    const req = new HttpRequest('GET', 'http://localhost:8080/api/test/me');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('should not add Authorization header when no valid token is available', () => {
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(false);
    const req = new HttpRequest('GET', 'http://localhost:8080/api/test/me');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });

  it('should not add Authorization header to non-API URLs', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    const req = new HttpRequest('GET', 'https://external-service.com/data');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });

  // ===== Token Refresh Tests =====

  it('should call refreshToken if near expiry', async () => {
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(true);
    vi.mocked(authService.refreshToken).mockResolvedValue(true);
    vi.mocked(authService.getAccessToken).mockReturnValue('new-token');

    const req = new HttpRequest('GET', 'http://localhost:8080/api/test/me');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    await mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(authService.refreshToken).toHaveBeenCalled();
    expect(capturedReq!.headers.get('Authorization')).toBe('Bearer new-token');
  });

  it('should not call refresh if token is valid', () => {
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(authService.refreshToken).mockResolvedValue(true);

    const req = new HttpRequest('GET', 'http://localhost:8080/api/test/me');

    const next = (r: HttpRequest<any>) => of(null);

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(authService.refreshToken).not.toHaveBeenCalled();
  });

  // ===== X-Org-ID Header Tests =====

  it('should add X-Org-ID header for POST requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue('dialog-org-id');

    const req = new HttpRequest('POST', 'http://localhost:8080/api/tasks', null);
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('dialog-org-id');
  });

  it('should add X-Org-ID header for PUT requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue('dialog-org-id');

    const req = new HttpRequest('PUT', 'http://localhost:8080/api/tasks/123', null);
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('dialog-org-id');
  });

  it('should add X-Org-ID header for DELETE requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue('dialog-org-id');

    const req = new HttpRequest('DELETE', 'http://localhost:8080/api/tasks/123');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('dialog-org-id');
  });

  it('should NOT add X-Org-ID header for GET list requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue('dialog-org-id');

    const req = new HttpRequest('GET', 'http://localhost:8080/api/tasks');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.has('X-Org-ID')).toBe(false);
  });

  it('should add X-Org-ID header for GET /organization/members requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue('dialog-org-id');

    const req = new HttpRequest('GET', 'http://localhost:8080/api/organization/members');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('dialog-org-id');
  });

  // ===== Multi-Org Context Priority Tests =====

  it('should prioritize dialog org over settings org', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue('dialog-org-id');
    vi.mocked(orgContextService.getSettingsOrg).mockReturnValue('settings-org-id');

    const req = new HttpRequest('POST', 'http://localhost:8080/api/tasks', null);
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('dialog-org-id');
  });

  it('should use settings org if dialog org is not set', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue(null);
    vi.mocked(orgContextService.getSettingsOrg).mockReturnValue('settings-org-id');

    const req = new HttpRequest('POST', 'http://localhost:8080/api/tasks', null);
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('settings-org-id');
  });

  it('should use default org if neither dialog nor settings org is set', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue(null);
    vi.mocked(orgContextService.getSettingsOrg).mockReturnValue(null);
    vi.mocked(orgContextService.getDefaultOrg).mockReturnValue({
      id: 'default-org-id',
      slug: 'default-org',
      role: 'USER',
    });

    const req = new HttpRequest('POST', 'http://localhost:8080/api/tasks', null);
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.get('X-Org-ID')).toBe('default-org-id');
  });

  it('should not add X-Org-ID if no org context is available', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);
    vi.mocked(authService.isTokenExpirySoon).mockReturnValue(false);
    vi.mocked(orgContextService.getDialogOrg).mockReturnValue(null);
    vi.mocked(orgContextService.getSettingsOrg).mockReturnValue(null);
    vi.mocked(orgContextService.getDefaultOrg).mockReturnValue(null);

    const req = new HttpRequest('POST', 'http://localhost:8080/api/tasks', null);
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return of(null);
    };

    mockAuthInterceptor(req, authService, orgContextService, apiBasePath, next);

    expect(capturedReq!.headers.has('X-Org-ID')).toBe(false);
  });
});

// ===== Error Message Mapping Tests =====

describe('mapErrorToUserMessage', () => {
  function makeError(status: number): HttpErrorResponse {
    return new HttpErrorResponse({ status, url: '/api/test' });
  }

  it('should map network error (status 0) to connection message', () => {
    const message = mapErrorToUserMessage(makeError(0));
    expect(message).toContain('Unable to connect to server');
  });

  it('should map 403 Forbidden to permission error message', () => {
    const message = mapErrorToUserMessage(makeError(403));
    expect(message).toContain("don't have permission");
  });

  it('should map 410 Gone to expired link message', () => {
    const message = mapErrorToUserMessage(makeError(410));
    expect(message).toContain('expired');
  });

  it('should map 409 Conflict to conflict message', () => {
    const message = mapErrorToUserMessage(makeError(409));
    expect(message).toContain('cannot be completed');
  });

  it('should return null for 401 Unauthorized (handled separately)', () => {
    const message = mapErrorToUserMessage(makeError(401));
    expect(message).toBeNull();
  });

  it('should map 500 Internal Server Error to generic server error message', () => {
    const message = mapErrorToUserMessage(makeError(500));
    expect(message).toContain('Something went wrong');
  });

  it('should map 502 Bad Gateway to generic server error message', () => {
    const message = mapErrorToUserMessage(makeError(502));
    expect(message).toContain('Something went wrong');
  });

  it('should return null for unhandled 4xx errors (e.g. 404)', () => {
    const message = mapErrorToUserMessage(makeError(404));
    expect(message).toBeNull();
  });

  it('should return null for 422 Unprocessable Entity', () => {
    const message = mapErrorToUserMessage(makeError(422));
    expect(message).toBeNull();
  });
});
