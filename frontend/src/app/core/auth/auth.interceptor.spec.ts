import { TestBed } from '@angular/core/testing';
import { HttpRequest } from '@angular/common/http';
import { EMPTY } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let authService: AuthService;

  beforeEach(() => {
    const authServiceMock = {
      getAccessToken: vi.fn(() => 'test-token'),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceMock }],
    });

    authService = TestBed.inject(AuthService);
  });

  it('should add Authorization header to API requests', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    const req = new HttpRequest('GET', 'http://localhost:8080/api/test/me');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return EMPTY;
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));

    expect(capturedReq!.headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('should not add Authorization header when no token is available', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('');
    const req = new HttpRequest('GET', 'http://localhost:8080/api/test/me');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return EMPTY;
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));

    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });

  it('should not add Authorization header to non-API URLs', () => {
    vi.mocked(authService.getAccessToken).mockReturnValue('test-token');
    const req = new HttpRequest('GET', 'https://external-service.com/data');
    let capturedReq: HttpRequest<any> | undefined = undefined;

    const next = (r: HttpRequest<any>) => {
      capturedReq = r;
      return EMPTY;
    };

    TestBed.runInInjectionContext(() => authInterceptor(req, next));

    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });
});
