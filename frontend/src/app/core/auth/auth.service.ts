import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { filter } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private oauthService = inject(OAuthService);
  private router = inject(Router);

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
}
