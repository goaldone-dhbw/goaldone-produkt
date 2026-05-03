import { TestBed } from '@angular/core/testing';
import { TenantService } from './tenant.service';
import { AuthService } from '../auth/auth.service';
import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('TenantService', () => {
  let service: TenantService;
  let authService: any;

  beforeEach(() => {
    const authServiceMock = {
      getOrganizations: vi.fn().mockReturnValue([])
    };

    TestBed.configureTestingModule({
      providers: [
        TenantService,
        { provide: AuthService, useValue: authServiceMock }
      ]
    });

    service = TestBed.inject(TenantService);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    sessionStorage.removeItem('activeOrgId');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Single Membership Auto-Selection', () => {
    it('should auto-select the only membership', () => {
      sessionStorage.removeItem('activeOrgId');

      authService.getOrganizations.mockReturnValue([
        { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' }
      ]);

      const newService = TestBed.runInInjectionContext(() => new TenantService());
      expect(newService.getActiveOrgId()).toBe('org-1');
    });
  });

  describe('Multiple Memberships', () => {
    it('should restore from sessionStorage if available', () => {
      sessionStorage.setItem('activeOrgId', 'org-2');

      authService.getOrganizations.mockReturnValue([
        { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' },
        { id: 'org-2', slug: 'org-two', role: 'USER' }
      ]);

      const newService = TestBed.runInInjectionContext(() => new TenantService());
      expect(newService.getActiveOrgId()).toBe('org-2');
    });

    it('should use first membership if nothing in storage', () => {
      sessionStorage.removeItem('activeOrgId');

      authService.getOrganizations.mockReturnValue([
        { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' },
        { id: 'org-2', slug: 'org-two', role: 'USER' }
      ]);

      const newService = TestBed.runInInjectionContext(() => new TenantService());
      expect(newService.getActiveOrgId()).toBe('org-1');
    });
  });

  describe('setActiveOrgId', () => {
    it('should update the active org ID and persist to sessionStorage', () => {
      service.setActiveOrgId('org-uuid');

      expect(service.getActiveOrgId()).toBe('org-uuid');
      expect(sessionStorage.getItem('activeOrgId')).toBe('org-uuid');
    });
  });

  describe('hasMultipleOrgs', () => {
    it('should return true when user has multiple memberships', () => {
      authService.getOrganizations.mockReturnValue([
        { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' },
        { id: 'org-2', slug: 'org-two', role: 'USER' }
      ]);

      const newService = TestBed.runInInjectionContext(() => new TenantService());
      expect(newService.hasMultipleOrgs()).toBe(true);
    });

    it('should return false when user has single membership', () => {
      authService.getOrganizations.mockReturnValue([
        { id: 'org-1', slug: 'org-one', role: 'COMPANY_ADMIN' }
      ]);

      const newService = TestBed.runInInjectionContext(() => new TenantService());
      expect(newService.hasMultipleOrgs()).toBe(false);
    });

    it('should return false when user has no memberships', () => {
      authService.getOrganizations.mockReturnValue([]);

      const newService = TestBed.runInInjectionContext(() => new TenantService());
      expect(newService.hasMultipleOrgs()).toBe(false);
    });
  });
});
