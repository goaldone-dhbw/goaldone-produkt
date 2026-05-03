import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Manages organization context selection for dialogs and pages.
 *
 * Purpose: Centralize org selection logic and support both dialog-scoped (D-09)
 * and page-scoped (D-10) selections.
 *
 * - Dialog-scoped context: Persists only for a single dialog lifecycle
 * - Page-scoped context: Persists for a page session (e.g., company settings)
 */
@Injectable({ providedIn: 'root' })
export class OrgContextService {
  private authService = inject(AuthService);

  // Dialog context: in-memory, cleared on dialog close
  private dialogOrgContext = new BehaviorSubject<string | null>(null);
  // Settings page context: persists for page session only
  private settingsOrgContext = new BehaviorSubject<string | null>(null);

  /**
   * Observable for dialog org context changes (D-09)
   */
  dialogOrgContext$ = this.dialogOrgContext.asObservable();

  /**
   * Observable for settings page org context changes (D-10)
   */
  settingsOrgContext$ = this.settingsOrgContext.asObservable();

  /**
   * Set the selected org for the current dialog.
   * @param orgId Organization ID to select
   */
  setDialogOrg(orgId: string): void {
    this.dialogOrgContext.next(orgId);
  }

  /**
   * Get the currently selected org in dialog context.
   * @returns Org ID or null if not selected
   */
  getDialogOrg(): string | null {
    return this.dialogOrgContext.getValue();
  }

  /**
   * Clear the dialog org selection when dialog closes.
   */
  clearDialogOrg(): void {
    this.dialogOrgContext.next(null);
  }

  /**
   * Set the selected org for the settings page.
   * @param orgId Organization ID to select
   */
  setSettingsOrg(orgId: string): void {
    this.settingsOrgContext.next(orgId);
  }

  /**
   * Get the currently selected org in settings page context.
   * @returns Org ID or null if not selected
   */
  getSettingsOrg(): string | null {
    return this.settingsOrgContext.getValue();
  }

  /**
   * Clear the settings page org selection when navigating away.
   */
  clearSettingsOrg(): void {
    this.settingsOrgContext.next(null);
  }

  /**
   * Check if user is member of multiple organizations.
   * Used to determine whether to show/hide org dropdowns.
   * @returns true if user has 2+ org memberships
   */
  hasMultipleOrgs(): boolean {
    return this.getOrganizations().length > 1;
  }

  /**
   * Get the list of organizations the user is member of.
   * Delegates to AuthService.
   * @returns Array of org objects with id, slug, and role
   */
  getOrganizations(): Array<{ id: string; slug: string; role: string }> {
    return this.authService.getOrganizations();
  }

  /**
   * Get the default org (first org in list).
   * Used when initializing context (D-12).
   * @returns First org object or null if user has no orgs
   */
  getDefaultOrg(): { id: string; slug: string; role: string } | null {
    return this.authService.getActiveOrganization();
  }

  /**
   * Validate that user is a member of the specified org.
   * @param orgId Organization ID to validate
   * @returns true if user is member of the org
   */
  validateOrgAccess(orgId: string): boolean {
    const orgs = this.getOrganizations();
    return orgs.some((org) => org.id === orgId);
  }
}
