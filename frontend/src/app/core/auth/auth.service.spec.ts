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
      setupAutomaticSilentRefresh: vi.fn(),
      hasValidAccessToken: vi.fn(() => true),
      getAccessToken: vi.fn(() => 'test-token'),
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
          scope: 'openid profile email offline_access urn:zitadel:iam:user:resourceowner',
          responseType: 'code',
          useSilentRefresh: false,
        })
      );
    });

    it('should setup automatic silent refresh', async () => {
      await service.initialize();

      expect(oauthService.setupAutomaticSilentRefresh).toHaveBeenCalled();
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
    it('should call OAuthService.logOut', () => {
      service.logout();

      expect(oauthService.logOut).toHaveBeenCalled();
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
      it('should return an empty array if no roles are present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserRoles()).toEqual([]);
      });

      it('should extract roles from "roles" claim', () => {
        const token = createMockJwt({ roles: ['admin', 'user'] });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserRoles()).toEqual(['admin', 'user']);
      });

      it('should extract roles from Zitadel-specific claim', () => {
        const token = createMockJwt({
          'urn:zitadel:iam:org:project:roles': ['editor'],
        });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserRoles()).toEqual(['editor']);
      });
    });

    describe('getUserOrganizationId', () => {
      it('should return null if no org ID is present', () => {
        const token = createMockJwt({ sub: '123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserOrganizationId()).toBeNull();
      });

      it('should extract org ID from "org_id" claim', () => {
        const token = createMockJwt({ org_id: 'org123' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserOrganizationId()).toBe('org123');
      });

      it('should extract org ID from "organisation_id" claim', () => {
        const token = createMockJwt({ organisation_id: 'org456' });
        vi.mocked(oauthService.getAccessToken).mockReturnValue(token);
        expect(service.getUserOrganizationId()).toBe('org456');
      });
    });
  });
});
