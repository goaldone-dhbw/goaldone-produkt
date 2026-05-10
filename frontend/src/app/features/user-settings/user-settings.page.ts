import { Component, OnInit, inject, ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Password } from 'primeng/password';
import { Message } from 'primeng/message';
import { Tooltip } from 'primeng/tooltip';
import { InputTextModule } from 'primeng/inputtext';
import { UserAccountsService, AccountResponse } from '../../api';
import { AuthService } from '../../core/auth/auth.service';
import { BasePopupComponent } from '../../shared/base-popup/base-popup.component';
import { UserSettingsAccountService } from '../../core/services/user-settings-account.service';

/**
 * Page component for managing the currently authenticated user's account settings.
 *
 * The page allows the user to change the password, log out and delete the own account.
 * It also displays the accounts linked to the current identity.
 */
@Component({
  selector: 'app-user-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Button,
    Password,
    Message,
    Tooltip,
    InputTextModule,
    BasePopupComponent,
  ],
  templateUrl: './user-settings.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserSettingsPage implements OnInit {
  private userAccountsService = inject(UserAccountsService);
  private accountService = inject(UserSettingsAccountService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  /** Indicates whether the linked accounts are currently being loaded. */
  loading = false;

  /** Error message shown when loading the linked accounts fails. */
  error: string | null = null;

  /** Accounts linked to the currently authenticated user identity. */
  accounts: AccountResponse[] = [];

  /** Current password entered by the user. */
  currentPassword = '';

  /** New password entered by the user. */
  newPassword = '';

  /** Repeated password used to confirm the new password. */
  confirmPassword = '';

  /** Indicates whether the current password input has been touched. */
  currentPasswordTouched = false;

  /** Indicates whether the new password input has been touched. */
  passwordTouched = false;

  /** Indicates whether the password confirmation input has been touched. */
  confirmPasswordTouched = false;

  /** Indicates whether a password change request is currently running. */
  passwordLoading = false;

  /** Indicates whether a logout request is currently running. */
  logoutLoading = false;

  /** Indicates whether an account deletion request is currently running. */
  deleteLoading = false;

  /** Success message shown after a successful password change. */
  passwordSuccess: string | null = null;

  /** Error message shown when the password change fails. */
  passwordError: string | null = null;

  /** Error message shown when logout fails. */
  logoutError: string | null = null;

  /** Error message shown when account deletion fails. */
  deleteError: string | null = null;

  /** Controls visibility of the logout confirmation dialog. */
  logoutDialogOpen = false;

  /** Controls visibility of the account deletion confirmation dialog. */
  deleteDialogOpen = false;

  /** Controls visibility of the password change dialog. */
  passwordDialogOpen = false;

  /** Controls visibility of the edit account dialog. */
  editAccountDialogOpen = false;

  /** Currently selected account for editing. */
  selectedAccount: AccountResponse | null = null;

  /** First name in the edit account form. */
  editFirstName = '';

  /** Last name in the edit account form. */
  editLastName = '';

  /** Email in the edit account form. */
  editEmail = '';

  /** Indicates whether an account update request is currently running. */
  editAccountLoading = false;

  /** Error message shown when account update fails. */
  editAccountError: string | null = null;

  /** Success message shown after successful account update. */
  editAccountSuccess: string | null = null;

  /** Text entered in the delete confirmation field. */
  deleteConfirmText = '';

  /** Controls visibility of the edit account confirmation dialog. */
  editConfirmDialogOpen = false;

  /** Original values for comparison in confirmation dialog. */
  editConfirmOriginal: { firstName: string; lastName: string; email: string } | null = null;

  /** New values shown in confirmation dialog. */
  editConfirmNew: { firstName: string; lastName: string; email: string } | null = null;

  /** Indicates that a deletion attempt failed because the user is the last admin. */
  lastAdminAttempted = false;

  /** Loads all linked accounts after the component has been initialized. */
  ngOnInit(): void {
    this.fetchAccounts();
  }

  /**
   * Checks whether the current password input is empty after being touched.
   * @returns True if the current password is missing and the field has been touched.
   */
  get currentPasswordMissing(): boolean {
    return this.currentPasswordTouched && this.currentPassword.trim().length === 0;
  }

  /** @returns True if the minimum password length is fulfilled. */
  get passwordHasMinimumLength(): boolean {
    return this.newPassword.length >= 8;
  }

  /** @returns True if the password contains an uppercase letter. */
  get passwordHasUppercaseLetter(): boolean {
    return /[A-Z]/.test(this.newPassword);
  }

  /** @returns True if the password contains a lowercase letter. */
  get passwordHasLowercaseLetter(): boolean {
    return /[a-z]/.test(this.newPassword);
  }

  /** @returns True if the password contains a number. */
  get passwordHasNumber(): boolean {
    return /\d/.test(this.newPassword);
  }

  /** @returns True if the password contains a symbol. */
  get passwordHasSymbol(): boolean {
    return /[^A-Za-z0-9]/.test(this.newPassword);
  }

  /** @returns True if the confirmation is not empty and matches the new password. */
  get passwordConfirmationMatches(): boolean {
    return this.confirmPassword.length > 0 && this.newPassword === this.confirmPassword;
  }

  /** @returns True if all password policy rules are fulfilled. */
  get passwordMeetsPolicy(): boolean {
    return (
      this.passwordHasMinimumLength &&
      this.passwordHasUppercaseLetter &&
      this.passwordHasLowercaseLetter &&
      this.passwordHasNumber &&
      this.passwordHasSymbol
    );
  }

  /** @returns True if a confirmation was entered but does not match the new password. */
  get passwordsDoNotMatch(): boolean {
    return this.confirmPassword.length > 0 && this.newPassword !== this.confirmPassword;
  }

  /** @returns True if current and new password are equal. */
  get passwordSameAsCurrent(): boolean {
    return (
      this.currentPassword.length > 0 &&
      this.newPassword.length > 0 &&
      this.currentPassword === this.newPassword
    );
  }

  /** @returns True if the form is invalid or a password request is already running. */
  get passwordFormInvalid(): boolean {
    return (
      this.currentPassword.trim().length === 0 ||
      !this.passwordMeetsPolicy ||
      this.confirmPassword.length === 0 ||
      this.newPassword !== this.confirmPassword ||
      this.currentPassword === this.newPassword ||
      this.passwordLoading
    );
  }


  /** @returns True if delete confirmation text matches "LÖSCHEN". */
  get deleteConfirmValid(): boolean {
    return this.deleteConfirmText === 'LÖSCHEN';
  }

  /**
   * Returns the accountId of the account the current user is logged in with.
   * Falls back to the first available account if no single "active" account
   * can be determined from the loaded list.
   */
  private get currentAccountId(): string | null {
    if (this.accounts.length === 0) {
      return null;
    }
    // Use the first account; extend this logic once the backend exposes a
    // "current account" flag in the AccountResponse.
    return this.accounts[0].accountId?.toString() ?? null;
  }

  /** Loads all accounts linked to the current user identity. */
  private fetchAccounts(): void {
    this.loading = true;
    this.error = null;

    this.userAccountsService.getMyAccounts().subscribe({
      next: (data) => {
        this.accounts = data.accounts ?? [];
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = this.getErrorMessage(err, 'Konten konnten nicht geladen werden.');
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /**
   * Maps an HTTP or runtime error to a user-facing error message.
   * @param error Error object returned by the failed request.
   * @param fallback Fallback message used if no specific message can be resolved.
   */
  private getErrorMessage(error: any, fallback: string): string {
    const status = error?.status;
    const errorBody = error?.error;

    // Status-code specific messages
    switch (status) {
      case 400:
        return errorBody?.message || errorBody?.detail || 'Die Anfrage ist ungültig.';
      case 401:
        return 'Deine Sitzung ist abgelaufen. Bitte melden dich erneut an.';
      case 403:
        return 'Du hast keine Berechtigung für diese Aktion.';
      case 404:
        return 'Die angeforderte Ressource existiert nicht.';
      case 409:
        return errorBody?.message || errorBody?.detail || 'Diese Aktion kann nicht ausgeführt werden.';
      case 500:
        return 'Ein Server-Fehler ist aufgetreten. Bitte versuche es später erneut.';
      case 502:
      case 503:
        return 'Der Service ist temporär nicht verfügbar. Bitte versuche es in wenigen Momenten erneut.';
      default:
        // Try to extract message from error body
        if (typeof errorBody === 'string') {
          return errorBody;
        }
        return errorBody?.message || errorBody?.detail || error?.message || fallback;
    }
  }

  /**
   * Validates the password form and sends the password change request.
   * The request is only sent when all client-side validation rules are fulfilled.
   */
  submitPasswordChange(): void {
    this.currentPasswordTouched = true;
    this.passwordTouched = true;
    this.confirmPasswordTouched = true;
    this.passwordSuccess = null;
    this.passwordError = null;

    if (this.passwordFormInvalid) {
      return;
    }

    this.passwordLoading = true;

    this.accountService
      .changePassword({
        oldPassword: this.currentPassword,
        newPassword: this.newPassword,
      })
      .subscribe({
        next: () => {
          this.passwordSuccess = 'Passwort wurde erfolgreich geändert.';
          this.currentPassword = '';
          this.newPassword = '';
          this.confirmPassword = '';
          this.currentPasswordTouched = false;
          this.passwordTouched = false;
          this.confirmPasswordTouched = false;
          this.passwordLoading = false;
          this.passwordDialogOpen = false;
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.passwordError = this.getErrorMessage(err, 'Passwort konnte nicht geändert werden.');
          this.passwordLoading = false;
          this.cdr.detectChanges();
        },
      });
  }

  /** Opens the logout confirmation dialog unless a logout request is already running. */
  openLogoutDialog(): void {
    if (this.logoutLoading) {
      return;
    }
    this.logoutError = null;
    this.logoutDialogOpen = true;
  }

  /**
   * Confirms the logout action.
   * The service call completes immediately (no backend endpoint exists);
   * the actual session invalidation is handled by AuthService via Zitadel.
   */
  confirmLogout(): void {
    if (this.logoutLoading) {
      return;
    }

    this.logoutLoading = true;
    this.logoutError = null;

    this.accountService.logout().subscribe({
      next: () => {
        this.logoutLoading = false;
        this.logoutDialogOpen = false;
        this.authService.logout();
      },
      error: (err) => {
        // logout() returns of(undefined) and never errors, but keep the
        // error handler for safety in case the implementation changes.
        this.logoutError = this.getErrorMessage(err, 'Abmelden nicht möglich.');
        this.logoutLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Opens the password change dialog. */
  openPasswordDialog(): void {
    this.passwordSuccess = null;
    this.passwordError = null;
    this.currentPasswordTouched = false;
    this.passwordTouched = false;
    this.confirmPasswordTouched = false;
    this.passwordDialogOpen = true;
  }

  /** Opens the edit account dialog and pre-fills fields. */
  openEditAccountDialog(account: AccountResponse): void {
    this.selectedAccount = account;
    this.editFirstName = account.firstName ?? '';
    this.editLastName = account.lastName ?? '';
    this.editEmail = account.email ?? '';
    this.editAccountError = null;
    this.editAccountSuccess = null;
    this.editAccountDialogOpen = true;
    this.cdr.markForCheck();
  }

  /** Shows the confirmation dialog with differences before submitting. */
  submitEditAccount(): void {
    if (!this.selectedAccount) {
      return;
    }

    this.editConfirmOriginal = {
      firstName: this.selectedAccount.firstName ?? '',
      lastName: this.selectedAccount.lastName ?? '',
      email: this.selectedAccount.email ?? '',
    };

    this.editConfirmNew = {
      firstName: this.editFirstName.trim(),
      lastName: this.editLastName.trim(),
      email: this.editEmail.trim(),
    };

    this.editConfirmDialogOpen = true;
    this.editAccountDialogOpen = false;
    this.cdr.markForCheck();
  }

  /** Confirms the account changes and submits them to the API. */
  confirmEditAccount(): void {
    if (!this.selectedAccount?.accountId || this.editAccountLoading) {
      return;
    }

    this.editAccountLoading = true;
    this.editAccountError = null;
    this.editAccountSuccess = null;

    this.userAccountsService
      .updateAccount(this.selectedAccount.accountId.toString(), {
        firstName: this.editFirstName.trim() || undefined,
        lastName: this.editLastName.trim() || undefined,
        email: this.editEmail.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.editAccountSuccess = 'Account erfolgreich aktualisiert.';
          this.editAccountLoading = false;
          this.editAccountDialogOpen = false;
          this.editConfirmDialogOpen = false;
          this.fetchAccounts();
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.editAccountError = this.getErrorMessage(err, 'Account konnte nicht aktualisiert werden.');
          this.editAccountLoading = false;
          this.cdr.detectChanges();
        },
      });
  }

  /** Opens the account deletion confirmation dialog unless deletion is blocked. */
  openDeleteDialog(): void {
    if (this.deleteLoading) {
      return;
    }
    this.deleteError = null;
    this.deleteConfirmText = '';
    this.deleteDialogOpen = true;
  }

  /**
   * Confirms the account deletion and sends the deletion request.
   * After successful deletion the user is logged out via AuthService.
   * If the deletion fails with 409 Conflict (last admin), the button is disabled for future attempts.
   */
  confirmDeleteAccount(): void {
    if (this.lastAdminAttempted || this.deleteLoading || !this.deleteConfirmValid) {
      return;
    }

    const accountId = this.currentAccountId;
    if (!accountId) {
      this.deleteError = 'Kein Account gefunden.';
      return;
    }

    this.deleteLoading = true;
    this.deleteError = null;

    this.accountService.deleteOwnAccount(accountId).subscribe({
      next: () => {
        this.deleteLoading = false;
        this.deleteDialogOpen = false;
        this.authService.logout();
      },
      error: (err) => {
        if (err?.status === 409) {
          this.lastAdminAttempted = true;
          this.deleteError = 'Du bist der letzte Admin. Der Account kann nicht gelöscht werden, solange du der einzige Admin bist.';
        } else {
          this.deleteError = this.getErrorMessage(err, 'Löschen nicht möglich.');
        }
        this.deleteLoading = false;
        this.cdr.detectChanges();
      },
    });
  }
}
