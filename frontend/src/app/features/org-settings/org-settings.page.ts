import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { Dropdown } from 'primeng/dropdown';
import { Button } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { AuthService } from '../../core/auth/auth.service';
import { OrgContextService } from '../../core/services/org-context.service';
import { MemberManagementService } from '../../api/api/memberManagement.service';
import { MemberResponse } from '../../api/model/memberResponse';
import { MemberRole } from '../../api/model/memberRole';

type OrgOption = {
  id: string;
  slug: string;
  role: string;
};

type RoleOption = {
  label: string;
  value: MemberRole;
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
  private readonly memberManagementService = inject(MemberManagementService);

  readonly userOrgs = signal<OrgOption[]>([]);
  readonly adminOrgs = signal<OrgOption[]>([]);
  readonly showOrgDropdown = signal(false);
  readonly selectedSettingsOrg = new BehaviorSubject<string | null>(null);

  readonly selectedOrg = signal<string | null>(null);

  // Member list state
  readonly members = signal<MemberResponse[]>([]);
  readonly membersLoading = signal(false);
  readonly membersError = signal<string | null>(null);

  // Invite form state
  readonly showInviteForm = signal(false);
  inviteEmail = '';
  inviteFirstName = '';
  inviteLastName = '';
  inviteRole: MemberRole = MemberRole.User;
  readonly inviteLoading = signal(false);
  readonly inviteError = signal<string | null>(null);

  // Role change state
  readonly roleChangeTarget = signal<MemberResponse | null>(null);
  selectedNewRole: MemberRole = MemberRole.User;
  readonly roleChangeLoading = signal(false);
  readonly roleChangeError = signal<string | null>(null);

  // Remove state
  readonly removeTarget = signal<MemberResponse | null>(null);
  readonly removeLoading = signal(false);
  readonly removeError = signal<string | null>(null);

  readonly roleOptions: RoleOption[] = [
    { label: 'Benutzer', value: MemberRole.User },
    { label: 'Administrator', value: MemberRole.CompanyAdmin },
  ];

  ngOnInit(): void {
    // Load user orgs
    this.userOrgs.set(this.authService.getOrganizations());

    // Filter for orgs where user has COMPANY_ADMIN role
    const adminOrgsList = this.userOrgs()
      .filter((org) => org.role === 'COMPANY_ADMIN')
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

  loadSettingsForOrg(orgId: string): void {
    this.membersLoading.set(true);
    this.membersError.set(null);
    this.memberManagementService.listMembers(orgId).subscribe({
      next: (response) => {
        this.members.set(response.members ?? []);
        this.membersLoading.set(false);
      },
      error: () => {
        this.membersError.set('Fehler beim Laden der Mitglieder');
        this.membersLoading.set(false);
      },
    });
  }

  getOrgName(orgId: string | null): string {
    if (!orgId) return '';
    return this.userOrgs().find((org) => org.id === orgId)?.slug || orgId;
  }

  getMemberDisplayName(member: MemberResponse): string {
    if (member.firstName || member.lastName) {
      return `${member.firstName ?? ''} ${member.lastName ?? ''}`.trim();
    }
    return member.email;
  }

  getRoleLabel(role: MemberRole): string {
    return role === MemberRole.CompanyAdmin ? 'Administrator' : 'Benutzer';
  }

  getStatusLabel(status: string): string {
    return status === 'INVITED' ? 'Eingeladen' : 'Aktiv';
  }

  // ── Invite member ──────────────────────────────────────────────

  openInviteForm(): void {
    this.inviteEmail = '';
    this.inviteFirstName = '';
    this.inviteLastName = '';
    this.inviteRole = MemberRole.User;
    this.inviteError.set(null);
    this.showInviteForm.set(true);
  }

  cancelInvite(): void {
    this.showInviteForm.set(false);
    this.inviteError.set(null);
  }

  submitInvite(): void {
    const orgId = this.orgContextService.getSettingsOrg();
    if (!orgId || !this.inviteEmail) return;

    this.inviteLoading.set(true);
    this.inviteError.set(null);

    this.memberManagementService
      .inviteMember(orgId, {
        email: this.inviteEmail,
        firstName: this.inviteFirstName,
        lastName: this.inviteLastName,
        role: this.inviteRole,
      })
      .subscribe({
        next: () => {
          this.showInviteForm.set(false);
          this.inviteLoading.set(false);
          this.loadSettingsForOrg(orgId);
        },
        error: (err) => {
          if (err.status === 409) {
            this.inviteError.set('Benutzer ist bereits Mitglied dieser Organisation');
          } else if (err.status === 403) {
            this.inviteError.set('Keine Berechtigung zum Einladen von Mitgliedern');
          } else {
            this.inviteError.set('Fehler beim Senden der Einladung');
          }
          this.inviteLoading.set(false);
        },
      });
  }

  // ── Change member role ─────────────────────────────────────────

  openRoleChange(member: MemberResponse): void {
    this.roleChangeTarget.set(member);
    this.selectedNewRole = member.role;
    this.roleChangeError.set(null);
  }

  cancelRoleChange(): void {
    this.roleChangeTarget.set(null);
    this.roleChangeError.set(null);
  }

  submitRoleChange(): void {
    const member = this.roleChangeTarget();
    const orgId = this.orgContextService.getSettingsOrg();
    if (!member || !orgId) return;

    this.roleChangeLoading.set(true);
    this.roleChangeError.set(null);

    this.memberManagementService
      .changeMemberRole(orgId, member.userId, { role: this.selectedNewRole })
      .subscribe({
        next: () => {
          this.roleChangeTarget.set(null);
          this.roleChangeLoading.set(false);
          this.loadSettingsForOrg(orgId);
        },
        error: (err) => {
          if (err.status === 409) {
            this.roleChangeError.set('Letzter Administrator kann nicht degradiert werden');
          } else if (err.status === 403) {
            this.roleChangeError.set('Keine Berechtigung zum Ändern von Rollen');
          } else {
            this.roleChangeError.set('Fehler beim Ändern der Rolle');
          }
          this.roleChangeLoading.set(false);
        },
      });
  }

  // ── Remove member ──────────────────────────────────────────────

  openRemoveConfirm(member: MemberResponse): void {
    this.removeTarget.set(member);
    this.removeError.set(null);
  }

  cancelRemove(): void {
    this.removeTarget.set(null);
    this.removeError.set(null);
  }

  confirmRemove(): void {
    const member = this.removeTarget();
    const orgId = this.orgContextService.getSettingsOrg();
    if (!member || !orgId) return;

    this.removeLoading.set(true);
    this.removeError.set(null);

    this.memberManagementService.removeMember(orgId, member.userId).subscribe({
      next: () => {
        this.removeTarget.set(null);
        this.removeLoading.set(false);
        this.loadSettingsForOrg(orgId);
      },
      error: (err) => {
        if (err.status === 409) {
          this.removeError.set('Letzter Administrator kann nicht entfernt werden');
        } else if (err.status === 403) {
          this.removeError.set('Keine Berechtigung oder Selbst-Entfernung nicht erlaubt');
        } else {
          this.removeError.set('Fehler beim Entfernen des Mitglieds');
        }
        this.removeLoading.set(false);
      },
    });
  }
}
