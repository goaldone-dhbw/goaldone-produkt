import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { Dropdown } from 'primeng/dropdown';
import { Button } from 'primeng/button';
import { Table, TableModule } from 'primeng/table';
import { AuthService } from '../../core/auth/auth.service';
import { OrgContextService } from '../../core/services/org-context.service';

type OrgOption = {
  id: string;
  slug: string;
  role: string;
};

/**
 * Company Settings page for organization administrators.
 *
 * - Shows org dropdown only if user is admin in 2+ orgs (D-08, D-10)
 * - Selected org persists for the page session and clears on navigation away
 * - Org-scoped actions (member management) use the selected org context
 */
@Component({
  selector: 'app-org-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, Dropdown, Button, TableModule],
  templateUrl: './org-settings.page.html',
  styleUrl: './org-settings.page.scss',
})
export class OrgSettingsPage implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly orgContextService = inject(OrgContextService);

  readonly userOrgs = signal<OrgOption[]>([]);
  readonly adminOrgs = signal<OrgOption[]>([]);
  readonly showOrgDropdown = signal(false);
  readonly selectedSettingsOrg = new BehaviorSubject<string | null>(null);

  readonly selectedOrg = signal<string | null>(null);

  readonly statuses = ['MEMBER', 'ADMIN', 'OWNER'];

  ngOnInit(): void {
    // Load user orgs
    this.userOrgs.set(this.authService.getOrganizations());

    // Filter for orgs where user has ROLE_ADMIN
    const adminOrgsList = this.userOrgs()
      .filter((org) => org.role === 'ROLE_ADMIN')
      .sort((a, b) => a.slug.localeCompare(b.slug));

    this.adminOrgs.set(adminOrgsList);

    // Check if dropdown should show (user is admin in 2+ orgs)
    this.showOrgDropdown.set(adminOrgsList.length > 1);

    // Set initial org to first admin org
    if (adminOrgsList.length > 0) {
      const initialOrgId = adminOrgsList[0].id;
      this.orgContextService.setSettingsOrg(initialOrgId);
      this.selectedSettingsOrg.next(initialOrgId);
      this.selectedOrg.set(initialOrgId);
      this.loadSettingsForOrg(initialOrgId);
    }
  }

  ngOnDestroy(): void {
    // Clear settings page context on navigation away (D-10)
    this.orgContextService.clearSettingsOrg();
  }

  onOrgSelected(orgId: string): void {
    // Update org context for this page session
    this.orgContextService.setSettingsOrg(orgId);
    this.selectedSettingsOrg.next(orgId);
    this.selectedOrg.set(orgId);

    // Reload page content for the selected org
    this.loadSettingsForOrg(orgId);
  }

  private loadSettingsForOrg(orgId: string): void {
    // TODO: Load organization members, settings, etc. for this org
    // This would call a service that uses X-Org-ID header via interceptor
    console.log('Loading settings for org:', orgId);
  }

  getOrgName(orgId: string): string {
    return this.userOrgs().find((org) => org.id === orgId)?.slug || orgId;
  }
}
