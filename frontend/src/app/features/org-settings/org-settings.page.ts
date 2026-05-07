import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';
import { ConfirmationService, MessageService } from 'primeng/api';
import { catchError, finalize, of } from 'rxjs';

import { MemberManagementService, MemberResponse, MemberRole } from '../../api';
import { AccountStore } from '../../core/accounts/account.store';

@Component({
  selector: 'app-org-settings',
  standalone: true,
  imports: [FormsModule, Card, Button, InputText, Message, TableModule, Tag, ConfirmDialog, Toast],
  providers: [ConfirmationService, MessageService],
  templateUrl: './org-settings.page.html',
  styleUrl: './org-settings.page.scss',
})
export class OrgSettingsPage {
  private readonly memberManagementService = inject(MemberManagementService);
  private readonly accountStore = inject(AccountStore);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);

  readonly inviteEmail = signal('');
  readonly inviteFirstName = signal('');
  readonly inviteLastName = signal('');

  readonly inviteError = signal<string | null>(null);
  readonly inviteSuccess = signal<string | null>(null);
  readonly inviteSending = signal(false);

  readonly members = signal<MemberResponse[]>([]);
  readonly membersLoading = signal(false);
  readonly membersError = signal<string | null>(null);

  readonly roleUpdatingUserId = signal<string | null>(null);
  readonly roleUpdateError = signal<string | null>(null);

  readonly deletingUserId = signal<string | null>(null);
  readonly deleteError = signal<string | null>(null);

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

  constructor() {
    effect(() => {
      const organizationId = this.orgId();

      if (!organizationId) return;

      this.loadMembers();
    });
  }

  loadMembers(): void {
    const organizationId = this.orgId();

    if (!organizationId) {
      this.membersError.set('Keine Organisation gefunden.');
      return;
    }

    this.membersLoading.set(true);
    this.membersError.set(null);

    this.memberManagementService
      .listMembers(organizationId)
      .pipe(
        finalize(() => this.membersLoading.set(false)),
        catchError((error) => {
          console.error('listMembers failed:', error);

          this.membersError.set('Nutzer konnten nicht geladen werden.');
          return of({ members: [] });
        }),
      )
      .subscribe((response) => {
        this.members.set(response.members ?? []);
      });
  }

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

        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Einladung wurde erfolgreich versendet.',
        });

        this.loadMembers();
      });
  }

  changeRole(member: MemberResponse): void {
    const organizationId = this.orgId();

    if (!organizationId) {
      this.roleUpdateError.set('Keine Organisation gefunden.');
      return;
    }

    if (this.roleUpdatingUserId() || this.deletingUserId()) return;

    const newRole = member.role === 'COMPANY_ADMIN' ? 'USER' : 'COMPANY_ADMIN';

    this.roleUpdatingUserId.set(member.zitadelUserId);
    this.roleUpdateError.set(null);
    this.deleteError.set(null);

    this.memberManagementService
      .changeMemberRole(organizationId, member.zitadelUserId, {
        role: newRole as MemberRole,
      })
      .pipe(
        finalize(() => this.roleUpdatingUserId.set(null)),
        catchError((error) => {
          console.error('changeMemberRole failed:', error);

          if (error.status === 403) {
            this.roleUpdateError.set('Sie haben keine Berechtigung, diese Rolle zu ändern.');
          } else if (error.status === 409) {
            this.roleUpdateError.set('Der letzte Admin kann nicht herabgestuft werden.');
          } else if (error.status === 500) {
            this.roleUpdateError.set(
              'Rolle konnte wegen eines Serverfehlers nicht geändert werden.',
            );
          } else {
            this.roleUpdateError.set('Rolle konnte nicht geändert werden.');
          }

          return of(false);
        }),
      )
      .subscribe((success) => {
        if (success === false) return;

        this.members.update((members) =>
          members.map((currentMember) =>
            currentMember.zitadelUserId === member.zitadelUserId
              ? { ...currentMember, role: newRole as MemberRole }
              : currentMember,
          ),
        );

        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Rolle wurde erfolgreich geändert.',
        });
      });
  }

  confirmDeleteMember(member: MemberResponse): void {
    if (this.roleUpdatingUserId() || this.deletingUserId()) return;

    this.confirmationService.confirm({
      header: 'Nutzer entfernen',
      message: `Möchten Sie den Nutzer ${member.email} wirklich aus dem Unternehmen entfernen?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Löschen',
      rejectLabel: 'Abbrechen',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteMember(member),
    });
  }

  private deleteMember(member: MemberResponse): void {
    const organizationId = this.orgId();

    if (!organizationId) {
      this.deleteError.set('Keine Organisation gefunden.');
      return;
    }

    this.deletingUserId.set(member.zitadelUserId);
    this.deleteError.set(null);
    this.roleUpdateError.set(null);

    this.memberManagementService
      .removeMember(organizationId, member.zitadelUserId)
      .pipe(
        finalize(() => this.deletingUserId.set(null)),
        catchError((error) => {
          console.error('removeMember failed:', error);

          if (error.status === 403) {
            this.deleteError.set('Sie haben keine Berechtigung, diesen Nutzer zu löschen.');
          } else if (error.status === 404) {
            this.deleteError.set('Nutzer wurde nicht gefunden.');
          } else if (error.status === 409) {
            this.deleteError.set('Dieser Nutzer kann aktuell nicht gelöscht werden.');
          } else {
            this.deleteError.set('Nutzer konnte nicht gelöscht werden.');
          }

          return of(false);
        }),
      )
      .subscribe((success) => {
        if (success === false) return;

        this.members.update((members) =>
          members.filter((currentMember) => currentMember.zitadelUserId !== member.zitadelUserId),
        );

        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Nutzer wurde erfolgreich entfernt.',
        });
      });
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString();
  }

  formatName(member: MemberResponse): string {
    const name = `${member.firstName ?? ''} ${member.lastName ?? ''}`.trim();
    return name || '-';
  }

  getRoleLabel(role: MemberRole): string {
    return role === 'COMPANY_ADMIN' ? 'Admin' : 'Benutzer';
  }

  getRoleSeverity(role: MemberRole): 'success' | 'secondary' {
    return role === 'COMPANY_ADMIN' ? 'success' : 'secondary';
  }
}
