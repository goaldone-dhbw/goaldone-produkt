import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { filter } from 'rxjs';
import { LoggerService } from '../logger.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private oauthService = inject(OAuthService);
  private router = inject(Router);
  private logger = inject(LoggerService);

  initialize(): Promise<boolean> {
    // Read from window.__env at runtime (injected via env.js before app boot)
    // Fallback to defaults if not set (for local development)
    const windowEnv = (window as any).__env || {};
    const issuerUri = windowEnv['issuerUri'] || 'http://localhost:9000';
    const clientId = windowEnv['clientId'] || 'goaldone-frontend';
    const isProd =
      window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1';

    this.oauthService.configure({
      issuer: issuerUri,
      clientId: clientId,
      responseType: 'code',
      redirectUri: window.location.origin + '/callback',
      postLogoutRedirectUri: window.location.origin,
      scope: 'openid profile email offline_access',
      useSilentRefresh: false,
      showDebugInformation: !isProd,
    });

    // Handle refresh token errors: clear storage + redirect to login
    this.oauthService.events
      .pipe(filter((e) => e.type === 'token_refresh_error' || e.type === 'token_error'))
      .subscribe(() => {
        this.oauthService.logOut(true); // noRedirectToLogoutUrl=true → just clears storage
        this.router.navigateByUrl('/');
        this.oauthService.initLoginFlow();
      });

    // Note: useSilentRefresh is false — token refresh is handled per-request in authInterceptor (D-03)

    return this.oauthService.loadDiscoveryDocumentAndTryLogin();
  }

  initLoginFlow(): void {
    this.oauthService.initLoginFlow();
  }

  logout(): void {
    this.oauthService.logOut();
  }

  hasValidAccessToken(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  getAccessToken(): string {
    return this.oauthService.getAccessToken();
  }

  private decodeJwtToken(token: string): any {
    try {
      if (!token) {
        return null;
      }
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null; // Not a valid JWT format (header.payload.signature)
      }
      const payload = parts[1];
      // Robust URL-safe Base64 decoding
      const decodedPayload = decodeURIComponent(atob(payload).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(decodedPayload);
    } catch (e) {
      this.logger.error("Error decoding JWT token:", e);
      return null;
    }
  }

  getDecodedAccessToken(): any {
    const accessToken = this.oauthService.getAccessToken();
    return this.decodeJwtToken(accessToken);
  }

  /**
   * Returns roles extracted from the 'authorities' claim, mapped by organization ID.
   * @returns Object where keys are org IDs and values are role arrays (e.g., { "org-uuid-1": ["ROLE_ADMIN"] })
   */
  getUserRoles(): { [orgId: string]: string[] } {
    const decodedToken = this.getDecodedAccessToken();
    if (!decodedToken) {
      return {};
    }

    const orgs = Array.isArray(decodedToken['orgs']) ? decodedToken['orgs'] : [];

    // Build a map: each org ID maps to the role from the orgs claim
    const rolesByOrg: { [orgId: string]: string[] } = {};
    for (const org of orgs) {
      if (org.id && org.role) {
        rolesByOrg[org.id] = [org.role];
      }
    }

    return rolesByOrg;
  }

  /**
   * Returns the user's organization memberships from the 'orgs' claim.
   * @returns Array of org objects with id, slug, and role
   */
  getOrganizations(): Array<{ id: string; slug: string; role: string }> {
    const decodedToken = this.getDecodedAccessToken();
    if (!decodedToken) {
      return [];
    }

    const orgs = decodedToken['orgs'];
    if (!Array.isArray(orgs)) {
      return [];
    }

    return orgs;
  }

  /**
   * Returns the first organization (default active org after login).
   * @returns First org object or null if user has no orgs
   */
  getActiveOrganization(): { id: string; slug: string; role: string } | null {
    const orgs = this.getOrganizations();
    return orgs.length > 0 ? orgs[0] : null;
  }

  /**
   * @deprecated Use getOrganizations() instead. Returns first org ID for backward compatibility.
   */
  getUserOrganizationId(): string | null {
    const activeOrg = this.getActiveOrganization();
    return activeOrg ? activeOrg.id : null;
  }

  /**
   * @deprecated Use getOrganizations() instead. Returns the same data for backward compatibility.
   */
  getUserMemberships(): any[] {
    return this.getOrganizations();
  }
}
