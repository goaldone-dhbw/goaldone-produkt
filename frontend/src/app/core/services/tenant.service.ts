import { Injectable, Signal, signal, computed, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/**
 * Service for managing tenant/organization context in a multi-tenant application.
 * Manages the active organization ID (which org the user is currently working in).
 * Provides a Signal-based reactive interface for frontend components.
 */
@Injectable({ providedIn: 'root' })
export class TenantService {
  private authService = inject(AuthService);

  /**
   * Signal holding the currently active organization ID.
   * Persisted in sessionStorage for tab-level persistence.
   */
  readonly activeOrgId: Signal<string | null> = signal(this.loadActiveOrgIdFromStorage());

  /**
   * Computed signal indicating whether the user has multiple organizations.
   */
  readonly hasMultipleOrgs: Signal<boolean> = computed(() => {
    const memberships = this.authService.getOrganizations();
    return memberships && memberships.length > 1;
  });

  constructor() {
    this.initializeActiveOrg();
  }

  /**
   * Initialize the active org ID based on user memberships from JWT.
   * Always prioritize memberships from the current JWT over stored values.
   * If the user has exactly one membership, auto-select it.
   * If multiple memberships exist, restore from sessionStorage or use first one.
   */
  private initializeActiveOrg(): void {
    const memberships = this.authService.getOrganizations();

    if (!memberships || memberships.length === 0) {
      // User has no memberships - don't use old stored values
      (this.activeOrgId as any).set(null);
      return;
    }

    if (memberships.length === 1) {
      // Auto-select single membership
      const orgId = memberships[0].id;
      (this.activeOrgId as any).set(orgId);
      this.saveActiveOrgIdToStorage(orgId);
      return;
    }

    // Multiple memberships: restore from storage ONLY if it still exists in current memberships
    const stored = this.loadActiveOrgIdFromStorage();
    if (stored && memberships.some(m => m.id === stored)) {
      (this.activeOrgId as any).set(stored);
    } else {
      // Default to first membership (ignore stored if it's from old JWT)
      const defaultOrgId = memberships[0].id;
      (this.activeOrgId as any).set(defaultOrgId);
      this.saveActiveOrgIdToStorage(defaultOrgId);
    }
  }

  /**
   * Reinitialize active org from the current JWT.
   * Call this after successful login to refresh org memberships from the new token.
   */
  refreshFromJWT(): void {
    console.log('[TenantService] Refreshing active org from JWT');
    this.initializeActiveOrg();
  }

  /**
   * Set the active organization ID.
   * @param orgId The organization ID to set as active.
   */
  setActiveOrgId(orgId: string): void {
    (this.activeOrgId as any).set(orgId);
    this.saveActiveOrgIdToStorage(orgId);
  }

  /**
   * Get the currently active organization ID.
   * @returns The active org ID, or null if none is selected.
   */
  getActiveOrgId(): string | null {
    return this.activeOrgId();
  }

  /**
   * Load the active org ID from sessionStorage.
   * @returns The stored org ID, or null if not found.
   */
  private loadActiveOrgIdFromStorage(): string | null {
    try {
      return sessionStorage.getItem('activeOrgId');
    } catch (e) {
      return null;
    }
  }

  /**
   * Save the active org ID to sessionStorage.
   * @param orgId The org ID to save.
   */
  private saveActiveOrgIdToStorage(orgId: string): void {
    try {
      sessionStorage.setItem('activeOrgId', orgId);
    } catch (e) {
      // Silently ignore storage errors
    }
  }
}
