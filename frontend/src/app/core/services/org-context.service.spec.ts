import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { OrgContextService } from './org-context.service';

describe('OrgContextService', () => {
  let service: OrgContextService;
  let authService: AuthService;

  const mockOrgs = [
    { id: 'org-1', slug: 'acme', role: 'COMPANY_ADMIN' },
    { id: 'org-2', slug: 'widgets', role: 'USER' },
  ];

  beforeEach(() => {
    const authServiceMock = {
      getOrganizations: vi.fn(() => mockOrgs),
      getActiveOrganization: vi.fn(() => mockOrgs[0]),
    };

    TestBed.configureTestingModule({
      providers: [OrgContextService, { provide: AuthService, useValue: authServiceMock }],
    });

    service = TestBed.inject(OrgContextService);
    authService = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Dialog-scoped org selection (D-09)', () => {
    it('should set and get dialog org', () => {
      service.setDialogOrg('org-1');
      expect(service.getDialogOrg()).toBe('org-1');
    });

    it('should start with null dialog org', () => {
      expect(service.getDialogOrg()).toBeNull();
    });

    it('should clear dialog org', () => {
      service.setDialogOrg('org-1');
      service.clearDialogOrg();
      expect(service.getDialogOrg()).toBeNull();
    });

    it('should emit dialog org changes via observable', () => {
      // BehaviorSubject emits synchronously — no done callback needed
      const values: (string | null)[] = [];
      service.dialogOrgContext$.subscribe((value) => {
        values.push(value);
      });

      service.setDialogOrg('org-1');
      service.setDialogOrg('org-2');
      service.clearDialogOrg();

      expect(values).toContain(null);
      expect(values).toContain('org-1');
      expect(values).toContain('org-2');
    });
  });

  describe('Settings page-scoped org selection (D-10)', () => {
    it('should set and get settings org', () => {
      service.setSettingsOrg('org-2');
      expect(service.getSettingsOrg()).toBe('org-2');
    });

    it('should start with null settings org', () => {
      expect(service.getSettingsOrg()).toBeNull();
    });

    it('should clear settings org', () => {
      service.setSettingsOrg('org-2');
      service.clearSettingsOrg();
      expect(service.getSettingsOrg()).toBeNull();
    });

    it('should emit settings org changes via observable', () => {
      // BehaviorSubject emits synchronously — no done callback needed
      const values: (string | null)[] = [];
      service.settingsOrgContext$.subscribe((value) => {
        values.push(value);
      });

      service.setSettingsOrg('org-1');
      service.setSettingsOrg('org-2');
      service.clearSettingsOrg();

      expect(values).toContain(null);
      expect(values).toContain('org-1');
      expect(values).toContain('org-2');
    });
  });

  describe('Helper methods', () => {
    it('should determine if user has multiple orgs', () => {
      expect(service.hasMultipleOrgs()).toBe(true);

      // Mock single org scenario
      vi.mocked(authService.getOrganizations).mockReturnValue([mockOrgs[0]]);
      expect(service.hasMultipleOrgs()).toBe(false);
    });

    it('should return organizations from AuthService', () => {
      const orgs = service.getOrganizations();
      expect(orgs).toEqual(mockOrgs);
      expect(authService.getOrganizations).toHaveBeenCalled();
    });

    it('should return default org from AuthService', () => {
      const defaultOrg = service.getDefaultOrg();
      expect(defaultOrg).toEqual(mockOrgs[0]);
      expect(authService.getActiveOrganization).toHaveBeenCalled();
    });

    it('should validate org access for member org', () => {
      expect(service.validateOrgAccess('org-1')).toBe(true);
      expect(service.validateOrgAccess('org-2')).toBe(true);
    });

    it('should reject org access for non-member org', () => {
      expect(service.validateOrgAccess('org-999')).toBe(false);
    });
  });

  describe('Multi-org UI logic patterns', () => {
    it('should support dialog initialization pattern', () => {
      // Simulates dialog opening
      service.clearDialogOrg();
      expect(service.getDialogOrg()).toBeNull();

      // User selects org
      service.setDialogOrg('org-1');
      expect(service.getDialogOrg()).toBe('org-1');

      // Dialog closes
      service.clearDialogOrg();
      expect(service.getDialogOrg()).toBeNull();
    });

    it('should support settings page initialization pattern', () => {
      // Page initializes and shows org dropdown if multi-org
      expect(service.hasMultipleOrgs()).toBe(true);

      // User selects org in dropdown
      service.setSettingsOrg(service.getDefaultOrg()?.id || '');
      expect(service.getSettingsOrg()).toBe('org-1');

      // User navigates away
      service.clearSettingsOrg();
      expect(service.getSettingsOrg()).toBeNull();
    });

    it('should use default org when dialog org is not set', () => {
      const defaultOrg = service.getDefaultOrg();
      expect(defaultOrg?.id).toBe('org-1');

      // Dialog can fallback to default if not explicitly set
      const selectedOrg = service.getDialogOrg() || service.getDefaultOrg()?.id;
      expect(selectedOrg).toBe('org-1');
    });

    it('should validate org selection in dialog context', () => {
      service.setDialogOrg('org-1');
      const selectedOrg = service.getDialogOrg();
      expect(service.validateOrgAccess(selectedOrg || '')).toBe(true);
    });
  });
});
