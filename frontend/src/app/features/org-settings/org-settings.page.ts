import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { catchError, finalize, of } from 'rxjs';

import { MemberManagementService, MemberRole } from '../../api';
import { AccountStore } from '../../core/accounts/account.store';

@Component({
  selector: 'app-org-settings',
  standalone: true,
  imports: [FormsModule, Card, Button, InputText, Message],
  templateUrl: './org-settings.page.html',
  styleUrl: './org-settings.page.scss',
})
export class OrgSettingsPage {
  private readonly memberManagementService = inject(MemberManagementService);
  private readonly accountStore = inject(AccountStore);

  readonly inviteEmail = signal('');
  readonly inviteFirstName = signal('');
  readonly inviteLastName = signal('');

  readonly inviteError = signal<string | null>(null);
  readonly inviteSuccess = signal<string | null>(null);
  readonly inviteSending = signal(false);

  readonly orgId = computed(() => {
    const adminAccount = this.accountStore
      .accounts()
      .find((account) => account.roles.includes('COMPANY_ADMIN'));

    return adminAccount?.organizationId ?? null;
  });

  readonly isInviteEmailValid = computed(() =>
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.inviteEmail().trim()),
  );

  readonly isInviteFirstNameValid = computed(() => {
    const firstName = this.inviteFirstName().trim();
    return firstName.length > 0 && firstName.length <= 200;
  });

  readonly isInviteLastNameValid = computed(() => {
    const lastName = this.inviteLastName().trim();
    return lastName.length > 0 && lastName.length <= 200;
  });

  readonly isInviteFormValid = computed(
    () =>
      this.isInviteEmailValid() && this.isInviteFirstNameValid() && this.isInviteLastNameValid(),
  );

  sendInvitation(): void {
    if (this.inviteSending()) return;

    this.inviteError.set(null);
    this.inviteSuccess.set(null);

    const organizationId = this.orgId();

    if (!organizationId) {
      this.inviteError.set('Keine Organisation gefunden.');
      return;
    }

    if (!this.isInviteEmailValid()) {
      this.inviteError.set('Bitte geben Sie eine gültige E-Mail-Adresse ein.');
      return;
    }

    if (!this.isInviteFirstNameValid() || !this.isInviteLastNameValid()) {
      this.inviteError.set(
        'Vorname und Nachname dürfen nicht leer sein und maximal 200 Zeichen enthalten.',
      );
      return;
    }

    this.inviteSending.set(true);

    this.memberManagementService
      .inviteMember(organizationId, {
        email: this.inviteEmail().trim(),
        firstName: this.inviteFirstName().trim(),
        lastName: this.inviteLastName().trim(),
        role: MemberRole.User,
      })
      .pipe(
        finalize(() => this.inviteSending.set(false)),
        catchError((error) => {
          this.inviteSuccess.set(null);

          if (error.status === 409) {
            this.inviteError.set('Für diese E-Mail existiert bereits eine Einladung.');
          } else if (error.status === 400) {
            this.inviteError.set('Bitte prüfen Sie die eingegebenen Daten.');
          } else {
            this.inviteError.set('Einladung konnte nicht gesendet werden.');
          }

          return of(false);
        }),
      )
      .subscribe((success) => {
        if (success === false) return;

        this.inviteEmail.set('');
        this.inviteFirstName.set('');
        this.inviteLastName.set('');
        this.inviteError.set(null);
        this.inviteSuccess.set('Einladung wurde erfolgreich versendet.');
      });
  }
}
