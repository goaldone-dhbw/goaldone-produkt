import { inject, Injectable } from '@angular/core';
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
    // Fallback to defaults if not set (for development)
    const windowEnv = (window as any).__env || {};
    const issuerUri = windowEnv['issuerUri'] || 'https://sso.dev.goaldone.de';
    const clientId = windowEnv['clientId'] || 'YOUR_ZITADEL_CLIENT_ID';
    const isProd = window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1';

    this.oauthService.configure({
      issuer: issuerUri,
      clientId: clientId,
      responseType: 'code',
      redirectUri: window.location.origin,
      scope: 'openid profile email offline_access urn:zitadel:iam:user:resourceowner',
      useSilentRefresh: false,
      showDebugInformation: !isProd,
    });

    // Handle refresh token errors: clear storage + redirect to login
    this.oauthService.events
      .pipe(
        filter((e) => e.type === 'token_refresh_error' || e.type === 'token_error')
      )
      .subscribe(() => {
        this.oauthService.logOut(true); // noRedirectToLogoutUrl=true → just clears storage
        this.router.navigateByUrl('/');
        this.oauthService.initLoginFlow();
      });

    this.oauthService.setupAutomaticSilentRefresh();

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

  getUserRoles(): string[] {
    const decodedToken = this.getDecodedAccessToken();
    const rolesObj = decodedToken?.['urn:zitadel:iam:org:project:368981415120863239:roles'] || {};
    return typeof rolesObj === 'object' && !Array.isArray(rolesObj)
      ? Object.keys(rolesObj)
      : Array.isArray(rolesObj)
        ? rolesObj
        : [];
  }

  getUserOrganizationId(): string | null {
    const decodedToken = this.getDecodedAccessToken();
    return (
      decodedToken?.['org_id'] || decodedToken?.['urn:zitadel:iam:user:resourceowner:id'] || null
    );
  }
}
