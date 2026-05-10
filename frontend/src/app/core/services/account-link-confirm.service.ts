import { Inject, Injectable, Optional } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

import { BASE_PATH } from '../../api/variables';
import { Configuration } from '../../api/configuration';

export interface LinkTokenResponse {
  linkToken: string;
  expiresAt?: string;
}

@Injectable({
  providedIn: 'root',
})
export class AccountLinkConfirmService {
  constructor(
    private readonly http: HttpClient,
    @Optional() @Inject(BASE_PATH) private readonly basePath: string | string[] | null,
    @Optional() private readonly configuration: Configuration | null,
  ) {}

  requestAccountLinkWithToken(accountAToken: string): Observable<LinkTokenResponse> {
    const headers = this.createAuthHeaders(accountAToken);

    return this.http.post<LinkTokenResponse>(
      this.buildUrl('/users/accounts/links/request'),
      {},
      { headers },
    );
  }

  confirmAccountLinkWithToken(linkToken: string, accountBToken: string): Observable<void> {
    const headers = this.createAuthHeaders(accountBToken);

    return this.http.post<void>(
      this.buildUrl('/users/accounts/links/confirm'),
      { linkToken },
      { headers },
    );
  }

  unlinkAccountWithToken(accountId: string, accountAToken: string): Observable<void> {
    const headers = this.createAuthHeaders(accountAToken);

    return this.http.delete<void>(
      this.buildUrl(`/users/accounts/links/${encodeURIComponent(accountId)}`),
      { headers },
    );
  }

  private createAuthHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    });
  }

  private buildUrl(path: string): string {
    const basePath = this.getBasePath().replace(/\/$/, '');
    return `${basePath}${path}`;
  }

  private getBasePath(): string {
    if (this.configuration?.basePath) {
      return this.configuration.basePath;
    }

    if (Array.isArray(this.basePath)) {
      return this.basePath[0];
    }

    if (this.basePath) {
      return this.basePath;
    }

    return 'http://localhost:8080/api/v1';
  }
}
