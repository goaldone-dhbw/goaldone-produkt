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
    this.configureOAuth('/callback');

    this.oauthService.events
      .pipe(filter((e) => e.type === 'token_refresh_error' || e.type === 'token_error'))
      .subscribe(() => {
        this.oauthService.logOut(true);
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

  async startAccountLinkingLogin(): Promise<void> {
    this.configureOAuth('/link-callback');

    console.log(
      'Starting account linking OIDC login with redirect URI:',
      window.location.origin + '/link-callback',
    );

    await this.oauthService.loadDiscoveryDocument();

    this.oauthService.initCodeFlow('accountLinking=true', {
      prompt: 'login',
    });
  }

  async handleAccountLinkingCallback(): Promise<void> {
    this.configureOAuth('/link-callback');

    await this.oauthService.loadDiscoveryDocumentAndTryLogin();

    if (!this.oauthService.hasValidAccessToken()) {
      throw new Error('No valid access token after account linking callback.');
    }
  }

  restoreAccessToken(token: string): void {
    if (!token) {
      return;
    }

    this.configureOAuth('/callback');

    const decodedToken = this.decodeJwtToken(token);
    const expiresAt = decodedToken?.exp ? decodedToken.exp * 1000 : Date.now() + 60 * 60 * 1000;
    const storedAt = Date.now();

    this.setOAuthStorageItem('access_token', token);
    this.setOAuthStorageItem('expires_at', String(expiresAt));
    this.setOAuthStorageItem('access_token_stored_at', String(storedAt));
  }

  getCurrentUserEmail(): string {
    const decodedToken = this.getDecodedAccessToken();

    return (
      decodedToken?.email ||
      decodedToken?.preferred_username ||
      decodedToken?.['urn:zitadel:iam:user:human:email'] ||
      ''
    );
  }

  getDecodedAccessToken(): any {
    const accessToken = this.oauthService.getAccessToken();
    return this.decodeJwtToken(accessToken);
  }

  getUserRoles(): string[] {
    const decodedToken = this.getDecodedAccessToken();

    if (!decodedToken) {
      return [];
    }

    const rolesKey = Object.keys(decodedToken).find(
      (key) =>
        key === 'roles' ||
        key === 'urn:zitadel:iam:org:project:roles' ||
        (key.startsWith('urn:zitadel:iam:org:project:') && key.endsWith(':roles')),
    );

    const rolesObj = rolesKey ? decodedToken[rolesKey] : {};

    return typeof rolesObj === 'object' && !Array.isArray(rolesObj)
      ? Object.keys(rolesObj)
      : Array.isArray(rolesObj)
        ? rolesObj
        : [];
  }

  getUserOrganizationId(): string | null {
    const decodedToken = this.getDecodedAccessToken();

    return (
      decodedToken?.['org_id'] ||
      decodedToken?.['organisation_id'] ||
      decodedToken?.['urn:zitadel:iam:user:resourceowner:id'] ||
      null
    );
  }

  private configureOAuth(redirectPath: string): void {
    const windowEnv = (window as any).__env || {};
    const issuerUri = windowEnv['issuerUri'] || 'https://sso.dev.goaldone.de';
    const clientId = windowEnv['clientId'] || 'YOUR_ZITADEL_CLIENT_ID';
    const isProd =
      window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1';

    this.oauthService.configure({
      issuer: issuerUri,
      clientId: clientId,
      responseType: 'code',
      redirectUri: window.location.origin + redirectPath,
      postLogoutRedirectUri: window.location.origin,
      scope: 'openid profile email offline_access urn:zitadel:iam:user:resourceowner',
      useSilentRefresh: false,
      showDebugInformation: !isProd,
    });
  }

  private setOAuthStorageItem(key: string, value: string): void {
    localStorage.setItem(key, value);
    sessionStorage.setItem(key, value);

    const oauthStorage = (this.oauthService as any)._storage;

    if (oauthStorage?.setItem) {
      oauthStorage.setItem(key, value);
    }
  }

  private decodeJwtToken(token: string): any {
    try {
      if (!token) {
        return null;
      }

      const parts = token.split('.');

      if (parts.length !== 3) {
        return null;
      }

      const payload = parts[1];
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const paddedBase64 = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');

      const decodedPayload = decodeURIComponent(
        atob(paddedBase64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join(''),
      );

      return JSON.parse(decodedPayload);
    } catch (e) {
      this.logger.error('Error decoding JWT token:', e);
      return null;
    }
  }
}
