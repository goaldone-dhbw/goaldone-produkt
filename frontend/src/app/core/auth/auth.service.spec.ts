import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let oauthService: OAuthService;
  let router: Router;
  let eventsSubject: Subject<any>;

  beforeEach(() => {
    eventsSubject = new Subject();
    const oauthServiceMock = {
      configure: vi.fn(),
      loadDiscoveryDocumentAndTryLogin: vi.fn(() => Promise.resolve(true)),
      hasValidAccessToken: vi.fn(() => true),
      getAccessToken: vi.fn(() => 'test-token'),
      getRefreshToken: vi.fn(() => null), // revokeToken() no-op stub needs this
      initLoginFlow: vi.fn(),
      logOut: vi.fn(),
      events: eventsSubject.asObservable(),
    };
    const routerMock = {
      navigateByUrl: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: OAuthService, useValue: oauthServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });

    service = TestBed.inject(AuthService);
    oauthService = TestBed.inject(OAuthService);
    router = TestBed.inject(Router);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('initialize', () => {
    it('should configure OAuthService with correct settings', async () => {
      await service.initialize();

      expect(oauthService.configure).toHaveBeenCalledWith(
        expect.objectContaining({
          scope: 'openid profile email offline_access',
          responseType: 'code',
          useSilentRefresh: false,
        }),
      );
    });


    it('should handle token_refresh_error event', async () => {
      await service.initialize();
      eventsSubject.next({ type: 'token_refresh_error' });

      expect(oauthService.logOut).toHaveBeenCalledWith(true);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/');
      expect(oauthService.initLoginFlow).toHaveBeenCalled();
    });

    it('should handle token_error event', async () => {
      await service.initialize();
      eventsSubject.next({ type: 'token_error' });

      expect(oauthService.logOut).toHaveBeenCalledWith(true);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/');
      expect(oauthService.initLoginFlow).toHaveBeenCalled();
    });
  });

  describe('hasValidAccessToken', () => {
    it('should delegate to OAuthService.hasValidAccessToken', () => {
      const result = service.hasValidAccessToken();

      expect(result).toBe(true);
      expect(oauthService.hasValidAccessToken).toHaveBeenCalled();
    });
  });

  describe('getAccessToken', () => {
    it('should delegate to OAuthService.getAccessToken', () => {
      const result = service.getAccessToken();

      expect(result).toBe('test-token');
      expect(oauthService.getAccessToken).toHaveBeenCalled();
    });
  });

  describe('initLoginFlow', () => {
    it('should call OAuthService.initLoginFlow', () => {
      service.initLoginFlow();

      expect(oauthService.initLoginFlow).toHaveBeenCalled();
    });
  });

  describe('logout', () => {
    it('should call OAuthService.logOut after revokeToken resolves (no-op stub)', async () => {
      // revokeToken() is a no-op: getRefreshToken returns null → resolves immediately.
      // async fn + Promise.resolve() chain requires multiple microtask ticks.
      service.logout();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      expect(oauthService.logOut).toHaveBeenCalledWith(true);
    });
  });

  describe('Token Information Extraction', () => {
    const createMockJwt = (payload: any): string => {
      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const payloadStr = btoa(JSON.stringify(payload));
      const signature = 'mock-signature';
      return `${header}.${payloadStr}.${signature}`;
    };

    it('should return null if no access token is available', () => {
      vi.mocked(oauthService.getAccessToken).mockReturnValue('');
      expect(service.getDecodedAccessToken()).toBeNull();
    });

    it('should return null if token format is invalid', () => {
      vi.mocked(oauthService.getAccessToken).mockReturnValue('invalid.token');
      expect(service.getDecodedAccessToken()).toBeNull();
    });

    it('should decode a valid JWT token payload', () => {
      const payload = { sub: '123', name: 'John Doe' };
      const token = createMockJwt(payload);
      vi.mocked(oauthService.getAccessToken).mockReturnValue(token);

      const decoded = service.getDecodedAccessToken();
      expect(decoded).toEqual(payload);
    });

    describe('getUserRoles', () => {
      it('should return an empty object if no orgs are present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserRoles()).toEqual({});
      });

      it('should return per-org role mapping from orgs claim', () => {
        const orgs = [
          { id: 'org-uuid-1', slug: 'acme', role: 'COMPANY_ADMIN' },
          { id: 'org-uuid-2', slug: 'widgets', role: 'USER' },
        ];
        const token = createMockJwt({ orgs });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserRoles()).toEqual({
          'org-uuid-1': ['COMPANY_ADMIN'],
          'org-uuid-2': ['USER'],
        });
      });

      it('should return empty object if orgs claim is not an array', () => {
        const token = createMockJwt({ orgs: 'not-an-array' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserRoles()).toEqual({});
      });
    });

    describe('getOrganizations', () => {
      it('should return empty array if no orgs are present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getOrganizations()).toEqual([]);
      });

      it('should return empty array if no token is available', () => {
        vi.mocked(oauthService.getAccessToken).mockReturnValue('');
        expect(service.getOrganizations()).toEqual([]);
      });

      it('should extract orgs from "orgs" claim', () => {
        const orgs = [
          { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' },
          { id: 'org-2', slug: 'org-two', role: 'USER' },
        ];
        const token = createMockJwt({ orgs });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getOrganizations()).toEqual(orgs);
      });

      it('should return empty array if orgs claim is not an array', () => {
        const token = createMockJwt({ orgs: 'not-an-array' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getOrganizations()).toEqual([]);
      });
    });

    describe('getActiveOrganization', () => {
      it('should return null if no orgs are present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getActiveOrganization()).toBeNull();
      });

      it('should return first org if orgs are present', () => {
        const orgs = [
          { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' },
          { id: 'org-2', slug: 'org-two', role: 'USER' },
        ];
        const token = createMockJwt({ orgs });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getActiveOrganization()).toEqual(orgs[0]);
      });
    });

    describe('getUserOrganizationId', () => {
      it('should return null if no orgs are present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserOrganizationId()).toBeNull();
      });

      it('should return first org ID from orgs claim (backward compatibility)', () => {
        const orgs = [
          { id: 'org-uuid-1', slug: 'acme', role: 'COMPANY_ADMIN' },
          { id: 'org-uuid-2', slug: 'widgets', role: 'USER' },
        ];
        const token = createMockJwt({ orgs });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserOrganizationId()).toBe('org-uuid-1');
      });
    });

    describe('getUserMemberships', () => {
      it('should return empty array if no memberships are present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserMemberships()).toEqual([]);
      });

      it('should return empty array if no token is available', () => {
        vi.mocked(oauthService.getAccessToken).mockReturnValue('');
        expect(service.getUserMemberships()).toEqual([]);
      });

      it('should extract memberships from "orgs" claim', () => {
        const memberships = [
          { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' },
          { id: 'org-2', slug: 'org-two', role: 'USER' }
        ];
        const token = createMockJwt({ orgs: memberships });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);

        expect(service.getUserMemberships()).toEqual(memberships);
      });

      it('should return empty array if orgs claim is not an array', () => {
        const token = createMockJwt({ orgs: 'not-an-array' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserMemberships()).toEqual([]);
      });
    });
  });
});
