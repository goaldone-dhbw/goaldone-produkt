import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HTTP_INTERCEPTORS, HttpContextToken } from '@angular/common/http';
import { tenantInterceptor } from './tenant.interceptor';
import { TenantService } from '../services/tenant.service';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// Define the context token for testing
const OVERRIDE_ORG_ID = new HttpContextToken<string>(() => '');

describe('tenantInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let tenantService: any;

  beforeEach(() => {
    const tenantServiceMock = {
      getActiveOrgId: vi.fn()
    };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: TenantService, useValue: tenantServiceMock },
        {
          provide: HTTP_INTERCEPTORS,
          useValue: tenantInterceptor,
          multi: true
        }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
    tenantService = TestBed.inject(TenantService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should attach X-Org-ID header when active org ID is set', async () => {
    tenantService.getActiveOrgId.mockReturnValue('org-uuid-123');

    httpClient.get('/api/tasks').subscribe();

    const req = httpTestingController.expectOne('/api/tasks');
    expect(req.request.headers.get('X-Org-ID')).toBe('org-uuid-123');
    req.flush({});
  });

  it('should not attach header when active org ID is null', async () => {
    tenantService.getActiveOrgId.mockReturnValue(null);

    httpClient.get('/api/tasks').subscribe();

    const req = httpTestingController.expectOne('/api/tasks');
    expect(req.request.headers.has('X-Org-ID')).toBe(false);
    req.flush({});
  });

  it('should handle multiple requests with consistent header', async () => {
    tenantService.getActiveOrgId.mockReturnValue('org-uuid-456');

    httpClient.get('/api/tasks').subscribe();
    httpClient.get('/api/users').subscribe();

    const req1 = httpTestingController.expectOne('/api/tasks');
    const req2 = httpTestingController.expectOne('/api/users');

    expect(req1.request.headers.get('X-Org-ID')).toBe('org-uuid-456');
    expect(req2.request.headers.get('X-Org-ID')).toBe('org-uuid-456');

    req1.flush({});
    req2.flush({});
  });
});
