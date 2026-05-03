import { TestBed } from '@angular/core/testing';
import { TenantService } from './tenant.service';
import { AuthService } from '../auth/auth.service';
import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('TenantService', () => {
  let service: TenantService;
  let authService: any;

  beforeEach(() => {
    const authServiceMock = {
      getUserMemberships: vi.fn()
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
      authService.getUserMemberships.mockReturnValue([
        { id: 'org-1', name: 'Org One', slug: 'org-one', role: 'ADMIN' }
      ]);

      const newService = new TenantService();
      expect(newService.getActiveOrgId()).toBe('org-1');
    });
  });

  describe('Multiple Memberships', () => {
    it('should restore from sessionStorage if available', () => {
      sessionStorage.setItem('activeOrgId', 'org-2');

      authService.getUserMemberships.mockReturnValue([
        { id: 'org-1', name: 'Org One', slug: 'org-one', role: 'ADMIN' },
        { id: 'org-2', name: 'Org Two', slug: 'org-two', role: 'MEMBER' }
      ]);

      const newService = new TenantService();
      expect(newService.getActiveOrgId()).toBe('org-2');
    });

    it('should use first membership if nothing in storage', () => {
      sessionStorage.removeItem('activeOrgId');

      authService.getUserMemberships.mockReturnValue([
        { id: 'org-1', name: 'Org One', slug: 'org-one', role: 'ADMIN' },
        { id: 'org-2', name: 'Org Two', slug: 'org-two', role: 'MEMBER' }
      ]);

      const newService = new TenantService();
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
      authService.getUserMemberships.mockReturnValue([
        { id: 'org-1', name: 'Org One', slug: 'org-one', role: 'ADMIN' },
        { id: 'org-2', name: 'Org Two', slug: 'org-two', role: 'MEMBER' }
      ]);

      const newService = new TenantService();
      expect(newService.hasMultipleOrgs()).toBe(true);
    });

    it('should return false when user has single membership', () => {
      authService.getUserMemberships.mockReturnValue([
        { id: 'org-1', name: 'Org One', slug: 'org-one', role: 'ADMIN' }
      ]);

      const newService = new TenantService();
      expect(newService.hasMultipleOrgs()).toBe(false);
    });

    it('should return false when user has no memberships', () => {
      authService.getUserMemberships.mockReturnValue([]);

      const newService = new TenantService();
      expect(newService.hasMultipleOrgs()).toBe(false);
    });
  });
});
