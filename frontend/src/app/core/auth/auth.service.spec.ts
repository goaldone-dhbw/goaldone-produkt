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
          scope: 'openid profile email offline_access',
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
});
