import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';

type OrgInfo = {
  id: string;
  slug: string;
  role: string;
};

/**
 * Main/landing page displaying authenticated user's identity and org memberships.
 * Confirms user is logged in and shows what orgs they have access to.
 *
 * Purpose: Entry point after login, displays user context (D-12)
 */
@Component({
  selector: 'app-mainpage',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mainpage.html',
  styleUrl: './mainpage.scss',
})
export class MainPage implements OnInit {
  private readonly authService = inject(AuthService);

  readonly userEmail = signal<string | null>(null);
  readonly userId = signal<string | null>(null);
  readonly userOrgs = signal<OrgInfo[]>([]);
  readonly userRoles = signal<{ [orgId: string]: string[] }>({});
  readonly activeOrg = signal<OrgInfo | null>(null);

  readonly isAuthenticated = computed(() => !!this.userEmail());

  ngOnInit(): void {
    // Load user info from decoded token
    const decodedToken = this.authService.getDecodedAccessToken();
    if (decodedToken) {
      // Extract user email
      this.userEmail.set(decodedToken['primary_email'] || decodedToken['email'] || null);

      // Extract user ID
      this.userId.set(decodedToken['user_id'] || decodedToken['sub'] || null);
    }

    // Load user organizations and roles
    this.userOrgs.set(this.authService.getOrganizations());
    this.userRoles.set(this.authService.getUserRoles());
    this.activeOrg.set(this.authService.getActiveOrganization());
  }

  /**
   * Helper to get object keys for template iteration.
   */
  objectKeys(obj: any): string[] {
    return Object.keys(obj);
  }

  /**
   * Helper to find organization name by ID.
   */
  findOrgName(orgId: string): string {
    return this.userOrgs().find((org) => org.id === orgId)?.slug || orgId;
  }

  /**
   * Format role display (remove ROLE_ prefix for readability).
   */
  formatRole(role: string): string {
    if (role.startsWith('ROLE_')) {
      return role.substring(5);
    }
    return role;
  }
}
