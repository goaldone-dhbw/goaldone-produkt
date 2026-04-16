import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authService: AuthService;

  beforeEach(() => {
    const authServiceMock = {
      hasValidAccessToken: vi.fn(() => true),
      initLoginFlow: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceMock }],
    });

    authService = TestBed.inject(AuthService);
  });

  it('should return true when user has valid access token', () => {
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(true);

    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(result).toBe(true);
    expect(authService.hasValidAccessToken).toHaveBeenCalled();
  });

  it('should initiate login flow and return false when user has no valid token', () => {
    vi.mocked(authService.hasValidAccessToken).mockReturnValue(false);

    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(result).toBe(false);
    expect(authService.initLoginFlow).toHaveBeenCalled();
  });
});
