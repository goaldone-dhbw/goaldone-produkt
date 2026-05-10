import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AccountLinkingStorageService {
  private readonly linkTokenKey = 'pending_link_token';
  private readonly initiatorEmailKey = 'pending_link_initiator_email';
  private readonly accountATokenKey = 'account_a_token';

  savePendingLink(linkToken: string, initiatorEmail: string, accountAToken: string): void {
    this.clearPendingLink();

    localStorage.setItem(this.linkTokenKey, linkToken);
    localStorage.setItem(this.initiatorEmailKey, initiatorEmail);
    localStorage.setItem(this.accountATokenKey, accountAToken);
  }

  getLinkToken(): string | null {
    return localStorage.getItem(this.linkTokenKey);
  }

  getInitiatorEmail(): string | null {
    return localStorage.getItem(this.initiatorEmailKey);
  }

  getAccountAToken(): string | null {
    return localStorage.getItem(this.accountATokenKey);
  }

  hasPendingLink(): boolean {
    return !!this.getLinkToken() || !!this.getInitiatorEmail() || !!this.getAccountAToken();
  }

  clearPendingLink(): void {
    localStorage.removeItem(this.linkTokenKey);
    localStorage.removeItem(this.initiatorEmailKey);
    localStorage.removeItem(this.accountATokenKey);
  }
}
