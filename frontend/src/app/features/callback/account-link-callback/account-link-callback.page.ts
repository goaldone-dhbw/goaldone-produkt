import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { AccountLinkingStorageService } from '../../../core/services/account-linking-storage.service';
import { AccountLinkConfirmService } from '../../../core/services/account-link-confirm.service';

@Component({
  selector: 'app-account-link-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="p-8 max-w-xl mx-auto">
      <div class="p-6 bg-white border border-slate-200 rounded-2xl shadow-sm">
        <h1 class="text-xl font-bold mb-4 text-slate-900">Account-Verknüpfung</h1>

        @if (!error) {
          <p class="text-slate-600 flex items-center gap-2">
            <i class="pi pi-spin pi-spinner"></i>
            Verknüpfung wird vorbereitet...
          </p>
        }

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
  private router = inject(Router);

  error: string | null = null;

  ngOnInit(): void {
    this.handleCallback();
  }

  private async handleCallback(): Promise<void> {
    const accountAToken = this.accountLinkingStorage.getAccountAToken();

    try {
      await this.authService.handleAccountLinkingCallback();

      const linkToken = this.accountLinkingStorage.getLinkToken();
      const initiatorEmail = this.accountLinkingStorage.getInitiatorEmail();

      if (!linkToken || !accountAToken) {
        this.accountLinkingStorage.clearPendingLink();

        await this.router.navigate(['/app/settings'], {
          queryParams: {
            accountLinked: 'error',
            reason: 'Die Link-Anfrage ist unvollständig. Bitte erneut starten.',
          },
        });

        return;
      }

      const accountBToken = this.authService.getAccessToken();
      const accountBEmail = this.authService.getCurrentUserEmail();

      if (!accountBToken) {
        this.authService.restoreAccessToken(accountAToken);
        this.accountLinkingStorage.clearPendingLink();

        await this.router.navigate(['/app/settings'], {
          queryParams: {
            accountLinked: 'error',
            reason: 'Für Account B konnte kein gültiges Token gelesen werden.',
          },
        });

        return;
      }

      const confirmed = confirm(
        `Account ${accountBEmail || 'B'} erkannt. Mit ${initiatorEmail || 'Account A'} verknüpfen?`,
      );

      if (!confirmed) {
        this.authService.restoreAccessToken(accountAToken);
        this.accountLinkingStorage.clearPendingLink();

        await this.router.navigate(['/app/settings'], {
          queryParams: {
            accountLinked: 'error',
            reason: 'Die Account-Verknüpfung wurde abgebrochen.',
          },
        });

        return;
      }

      await firstValueFrom(
        this.accountLinkConfirmService.confirmAccountLinkWithToken(linkToken, accountBToken),
      );

      this.authService.restoreAccessToken(accountAToken);
      this.accountLinkingStorage.clearPendingLink();

      await this.router.navigate(['/app/settings'], {
        queryParams: {
          accountLinked: 'success',
        },
      });
    } catch (err) {
      if (accountAToken) {
        this.authService.restoreAccessToken(accountAToken);
      }

      this.accountLinkingStorage.clearPendingLink();

      await this.router.navigate(['/app/settings'], {
        queryParams: {
          accountLinked: 'error',
          reason: this.mapLinkError(err),
        },
      });
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
