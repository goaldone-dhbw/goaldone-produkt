import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { AccountLinkingStorageService } from '../../../core/services/account-linking-storage.service';
import { AccountLinkConfirmService } from '../../../core/services/account-link-confirm.service';
import { UserAccountsService } from '../../../api';

@Component({
  selector: 'app-account-link-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="p-8 max-w-xl mx-auto">
      <div class="p-6 bg-white border border-slate-200 rounded-2xl shadow-sm">
        <h1 class="text-xl font-bold mb-4 text-slate-900">Account-Verknüpfung</h1>

        <!-- Loading state (while preparing confirmation) -->
        @if (!confirmationData && !error) {
          <p class="text-slate-600 flex items-center gap-2">
            <i class="pi pi-spin pi-spinner"></i>
            Verknüpfung wird vorbereitet...
          </p>
        }

        <!-- Confirmation state -->
        @if (confirmationData && !error) {
          <div class="space-y-4">
            <p class="text-slate-700">
              Der folgende Account wird verknüpft:
            </p>

            <div class="p-4 bg-slate-50 border border-slate-200 rounded-lg space-y-3">
              <div class="flex items-center justify-center">
                <span class="font-semibold text-slate-700">{{ confirmationData.currentEmail }}</span>
              </div>
              <div class="flex items-center justify-center">
                <i class="pi pi-arrow-down text-slate-400"></i>
              </div>
              <div class="space-y-2">
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500 text-center">Verknüpft mit:</p>
                @for (email of confirmationData.initiatorEmails; track email) {
                  <div class="flex items-center justify-center">
                    <span class="font-semibold text-emerald-700 bg-emerald-50 px-3 py-2 rounded">{{ email }}</span>
                  </div>
                }
                @if (confirmationData.initiatorEmails.length === 0) {
                  <div class="flex items-center justify-center">
                    <span class="font-semibold text-slate-500 text-sm">(Keine bisherigen Verknüpfungen)</span>
                  </div>
                }
              </div>
            </div>

            @if (confirming) {
              <div class="p-4 bg-blue-50 border border-blue-200 rounded-lg flex items-center gap-2 text-blue-700">
                <i class="pi pi-spin pi-spinner"></i>
                <span>Verknüpfung wird durchgeführt...</span>
              </div>
            } @else {
              <div class="flex gap-3">
                <button
                  class="flex-1 px-4 py-2 bg-slate-200 text-slate-900 rounded-lg font-semibold hover:bg-slate-300 transition"
                  (click)="cancelLink()"
                  [disabled]="confirming"
                >
                  Abbrechen
                </button>
                <button
                  class="flex-1 px-4 py-2 bg-emerald-600 text-white rounded-lg font-semibold hover:bg-emerald-700 transition"
                  (click)="confirmLink()"
                  [disabled]="confirming"
                >
                  Verknüpfen
                </button>
              </div>
            }
          </div>
        }

        <!-- Error state -->
        @if (error) {
          <div class="p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-xl">
            {{ error }}
          </div>
        }
      </div>
    </div>
  `,
})
export class AccountLinkCallbackPage implements OnInit {
  private authService = inject(AuthService);
  private accountLinkingStorage = inject(AccountLinkingStorageService);
  private accountLinkConfirmService = inject(AccountLinkConfirmService);
  private userAccountsService = inject(UserAccountsService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  error: string | null = null;
  confirmationData: { currentEmail: string; initiatorEmails: string[] } | null = null;
  confirming = false;

  private pendingLinkToken: string | null = null;
  private pendingAccountAToken: string | null = null;
  private pendingAccountBToken: string | null = null;

  ngOnInit(): void {
    this.handleCallback();
  }

  private async handleCallback(): Promise<void> {
    const accountAToken = this.accountLinkingStorage.getAccountAToken();

    try {
      console.log('Starting account linking callback handler...');

      try {
        await this.authService.handleAccountLinkingCallback();
      } catch (authErr) {
        console.error('OAuth callback failed:', authErr);
        throw authErr;
      }

      const linkToken = this.accountLinkingStorage.getLinkToken();

      console.log('Callback complete. Link token:', linkToken ? 'exists' : 'missing', 'Account A token:', accountAToken ? 'exists' : 'missing');

      if (!linkToken || !accountAToken) {
        this.accountLinkingStorage.clearPendingLink();
        this.error = 'Die Link-Anfrage ist unvollständig. Bitte erneut starten.';
        this.cdr.markForCheck();
        return;
      }

      const accountBToken = this.authService.getAccessToken();

      if (!accountBToken) {
        this.authService.restoreAccessToken(accountAToken);
        this.accountLinkingStorage.clearPendingLink();
        this.error = 'Für Account B konnte kein gültiges Token gelesen werden.';
        this.cdr.markForCheck();
        return;
      }

      const linkInfo = await firstValueFrom(this.userAccountsService.getAccountLinkInfo(linkToken));

      console.log('LinkInfo Response:', linkInfo);

      this.pendingLinkToken = linkToken;
      this.pendingAccountAToken = accountAToken;
      this.pendingAccountBToken = accountBToken;

      this.confirmationData = {
        currentEmail: linkInfo.currentEmail || '',
        initiatorEmails: linkInfo.initiatorEmails || [],
      };

      console.log('Confirmation data set - Current:', linkInfo.currentEmail, 'Initiators:', linkInfo.initiatorEmails);
      this.cdr.markForCheck();
    } catch (err) {
      console.error('Account linking callback failed:', err);

      if (accountAToken) {
        this.authService.restoreAccessToken(accountAToken);
      }

      this.accountLinkingStorage.clearPendingLink();
      this.error = this.mapLinkError(err);
      this.cdr.markForCheck();
    }
  }

  async confirmLink(): Promise<void> {
    if (!this.pendingLinkToken || !this.pendingAccountAToken || !this.pendingAccountBToken) {
      this.error = 'Verknüpfung kann nicht durchgeführt werden. Bitte erneut starten.';
      return;
    }

    this.confirming = true;
    this.error = null;

    try {
      await firstValueFrom(
        this.accountLinkConfirmService.confirmAccountLinkWithToken(
          this.pendingLinkToken,
          this.pendingAccountBToken,
        ),
      );

      this.authService.restoreAccessToken(this.pendingAccountAToken);
      this.accountLinkingStorage.clearPendingLink();

      await this.router.navigate(['/app/settings'], {
        queryParams: {
          accountLinked: 'success',
        },
      });
    } catch (err) {
      this.confirming = false;
      this.error = this.mapLinkError(err);
    }
  }

  async cancelLink(): Promise<void> {
    if (this.pendingAccountAToken) {
      this.authService.restoreAccessToken(this.pendingAccountAToken);
    }

    this.accountLinkingStorage.clearPendingLink();

    await this.router.navigate(['/app/settings'], {
      queryParams: {
        accountLinked: 'error',
        reason: 'Die Account-Verknüpfung wurde abgebrochen.',
      },
    });
  }

  private extractEmailFromToken(token: string): string {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return '';

      const decoded = JSON.parse(atob(parts[1]));
      console.log('Token claims:', decoded);

      return (
        decoded.email ||
        decoded.preferred_username ||
        decoded['urn:zitadel:iam:user:resourceowner:name'] ||
        decoded.sub ||
        ''
      );
    } catch (err) {
      console.error('Error decoding token:', err);
      return '';
    }
  }

  private mapLinkError(error: any): string {
    const code = this.extractErrorCode(error);

    if (error?.status === 410 || code === 'LINK_TOKEN_EXPIRED') {
      return 'Link-Anfrage abgelaufen. Bitte erneut starten.';
    }

    if (code === 'ALREADY_LINKED') {
      return 'Diese Accounts sind bereits verknüpft.';
    }

    if (code === 'SAME_ORGANIZATION_LINK_NOT_ALLOWED') {
      return 'Sie haben bereits einen Account in dieser Organisation.';
    }

    if (error?.status === 409) {
      return 'Diese Accounts können nicht verknüpft werden. Sie sind entweder bereits verknüpft oder gehören zur gleichen Organisation.';
    }

    if (error?.status === 401 || error?.status === 403) {
      return 'Die Verknüpfung konnte wegen fehlender Berechtigung nicht abgeschlossen werden. Bitte erneut einloggen.';
    }

    return 'Verknüpfung fehlgeschlagen.';
  }

  private extractErrorCode(error: any): string | null {
    const rawError = error?.error;

    if (!rawError) {
      return error?.message ?? null;
    }

    if (typeof rawError === 'string') {
      try {
        const parsed = JSON.parse(rawError);

        return parsed?.code || parsed?.errorCode || parsed?.error || parsed?.message || rawError;
      } catch {
        return rawError;
      }
    }

    return rawError?.code || rawError?.errorCode || rawError?.error || rawError?.message || null;
  }
}
