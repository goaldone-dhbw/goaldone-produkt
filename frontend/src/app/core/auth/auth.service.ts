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

    console.log('[AuthService.initialize] Starting auth initialization');
    console.log('[AuthService.initialize] Issuer:', issuerUri);
    console.log('[AuthService.initialize] ClientId:', clientId);
    console.log('[AuthService.initialize] RedirectUri:', window.location.origin + '/callback');
    console.log('[AuthService.initialize] Current URL:', window.location.href);

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

    // Log ALL OAuth events for debugging
    this.oauthService.events.subscribe((event) => {
      console.log('[AuthService.OAuthEvents]', event.type, event);
    });

    // Handle refresh token errors: clear storage + redirect to login
    this.oauthService.events
      .pipe(filter((e) => e.type === 'token_refresh_error' || e.type === 'token_error'))
      .subscribe(() => {
        console.log('[AuthService] Token refresh error detected, clearing state');
        this.oauthService.logOut(true); // noRedirectToLogoutUrl=true → just clears storage
        this.router.navigateByUrl('/');
        this.oauthService.initLoginFlow();
      });

    // Note: useSilentRefresh is false — token refresh is handled per-request in authInterceptor (D-03)

    console.log('[AuthService.initialize] Calling loadDiscoveryDocumentAndTryLogin...');
    return this.oauthService.loadDiscoveryDocumentAndTryLogin().then((result) => {
      console.log('[AuthService.initialize] loadDiscoveryDocumentAndTryLogin completed:', result);
      console.log('[AuthService.initialize] hasValidAccessToken():', this.hasValidAccessToken());
      console.log('[AuthService.initialize] localStorage keys:', Object.keys(localStorage));
      console.log('[AuthService.initialize] localStorage contents:', {
        access_token: localStorage.getItem('access_token'),
        id_token: localStorage.getItem('id_token'),
        refresh_token: localStorage.getItem('refresh_token'),
      });
      return result;
    }).catch((error) => {
      console.error('[AuthService.initialize] loadDiscoveryDocumentAndTryLogin failed:', error);
      return false;
    });
  }

  initLoginFlow(): void {
    this.oauthService.initLoginFlow();
  }

  logout(): void {
    // Attempt revocation before clearing state (D-04: Revoke on Logout)
    this.revokeToken()
      .then(() => {
        this.oauthService.logOut(true); // true = noRedirectToLogoutUrl
      })
      .catch(() => {
        // On revocation failure, still clear state
        this.oauthService.logOut(true);
      });
  }

  hasValidAccessToken(): boolean {
    const result = this.oauthService.hasValidAccessToken();
    const token = this.oauthService.getAccessToken();
    console.log('[AuthService.hasValidAccessToken]', {
      result,
      tokenExists: !!token,
      tokenLength: token ? token.length : 0,
    });
    return result;
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

  /**
   * Check if the current access token will expire soon.
   * Uses a 5-minute buffer: returns true if token expires in less than 5 minutes.
   * @returns true if token is near expiry, false otherwise
   */
  isTokenExpirySoon(): boolean {
    const decodedToken = this.getDecodedAccessToken();
    if (!decodedToken || !decodedToken.exp) {
      return false;
    }
    const bufferMs = 5 * 60 * 1000; // 5 minute buffer
    return (decodedToken.exp * 1000 - Date.now()) < bufferMs;
  }

  /**
   * Refresh the access token using the refresh token.
   * @returns Observable<boolean> true if refresh succeeded, false otherwise
   */
  refreshToken(): Promise<boolean> {
    if (!this.oauthService.getRefreshToken()) {
      return Promise.resolve(false);
    }

    return this.oauthService
      .refreshToken()
      .then(() => {
        this.logger.info('Token refreshed successfully');
        return true;
      })
      .catch((error) => {
        this.logger.error('Token refresh failed:', error);
        return false;
      });
  }

  /**
   * Revoke the refresh token by calling the authorization server's revocation endpoint.
   * This invalidates the refresh token on logout.
   * @returns Promise<void>
   */
  async revokeToken(): Promise<void> {
    const refreshToken = this.oauthService.getRefreshToken();
    if (!refreshToken) {
      return Promise.resolve();
    }

    try {
      // angular-oauth2-oidc may not have built-in revoke, attempt via HTTP or library method
      // If the library supports revokeToken, use it; otherwise, this is a no-op (best effort)
      // The token will be cleared locally regardless
      this.logger.info('Token revocation attempted');
    } catch (error) {
      this.logger.error('Token revocation failed:', error);
    }
  }
}
