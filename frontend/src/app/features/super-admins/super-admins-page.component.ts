import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize } from 'rxjs';

import {
  OrgManagementService,
  OrganizationListItem,
  SuperAdminManagementService,
  SuperAdminResponse,
} from '../../api';
import { AddSuperAdminDialogComponent } from './dialog/add-super-admin-dialog.component';
import { CreateOrganizationCardComponent } from './create-organization/create-organization-card/create-organization-card';

@Component({
  selector: 'app-super-admins-page',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    Tooltip,
    ConfirmDialog,
    Toast,
    AddSuperAdminDialogComponent,
    CreateOrganizationCardComponent,
  ],
  templateUrl: './super-admins-page.component.html',
  providers: [ConfirmationService, MessageService],
})
export class SuperAdminsPageComponent implements OnInit {
  private readonly adminService = inject(SuperAdminManagementService);
  private readonly orgManagementService = inject(OrgManagementService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);

  readonly superAdmins = signal<SuperAdminResponse[]>([]);
  readonly organizations = signal<OrganizationListItem[]>([]);

  readonly isLoading = signal(false);
  readonly isOrganizationsLoading = signal(false);
  readonly deletingOrganizationIds = signal<Set<string>>(new Set());

  addDialogVisible = false;

  ngOnInit(): void {
    this.loadSuperAdmins();
    this.loadOrganizations();
  }

  onOrganizationCreated(): void {
  }

  loadSuperAdmins(): void {
    this.isLoading.set(true);
    this.adminService
      .listSuperAdmins()

    this.adminService
      .listSuperAdmins()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (admins) => this.superAdmins.set(admins),
        error: () =>
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: 'Super-Admins konnten nicht geladen werden.',
          }),
        next: (admins: SuperAdminResponse[]) => this.superAdmins.set(admins),
        error: () =>
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: 'Super-Admins konnten nicht geladen werden.',
          }),
      });
  }

  loadOrganizations(): void {
    this.isOrganizationsLoading.set(true);

    this.orgManagementService
      .listOrganizations()
      .pipe(finalize(() => this.isOrganizationsLoading.set(false)))
      .subscribe({
        next: (response) => this.organizations.set(response.organizations),
        error: (error: HttpErrorResponse) =>
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: this.mapOrganizationError(error, 'Unternehmen konnten nicht geladen werden.'),
          }),
      });
  }

  onOrganizationCreated(): void {
    this.loadOrganizations();
  }

  confirmDeleteOrganization(organization: OrganizationListItem): void {
    this.confirmationService.confirm({
      message: `Möchtest du das Unternehmen "${organization.name}" wirklich löschen? Alle zugehörigen Daten werden entfernt.`,
      header: 'Unternehmen löschen',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Löschen',
      rejectLabel: 'Abbrechen',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteOrganization(organization),
    });
  }

  isDeletingOrganization(organizationId: string): boolean {
    return this.deletingOrganizationIds().has(organizationId);
  }

  private deleteOrganization(organization: OrganizationListItem): void {
    if (this.isDeletingOrganization(organization.id)) {
      return;
    }

    this.addDeletingOrganizationId(organization.id);

    this.orgManagementService
      .deleteOrganization(organization.id)
      .pipe(finalize(() => this.removeDeletingOrganizationId(organization.id)))
      .subscribe({
        next: () => {
          this.organizations.update((organizations) =>
            organizations.filter((item) => item.id !== organization.id),
          );

          this.messageService.add({
            severity: 'success',
            summary: 'Unternehmen gelöscht',
            detail: `Das Unternehmen "${organization.name}" wurde gelöscht.`,
          });
        },
        error: (error: HttpErrorResponse) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: this.mapOrganizationError(
              error,
              `Das Unternehmen "${organization.name}" konnte nicht gelöscht werden.`,
            ),
          });
        },
      });
  }

  private addDeletingOrganizationId(organizationId: string): void {
    this.deletingOrganizationIds.update((ids) => {
      const nextIds = new Set(ids);
      nextIds.add(organizationId);
      return nextIds;
    });
  }

  private removeDeletingOrganizationId(organizationId: string): void {
    this.deletingOrganizationIds.update((ids) => {
      const nextIds = new Set(ids);
      nextIds.delete(organizationId);
      return nextIds;
    });
  }

  openAddDialog(): void {
    this.addDialogVisible = true;
  }

  onDialogVisibleChange(value: boolean): void {
    this.addDialogVisible = value;
  }

  onAdded(): void {
    this.addDialogVisible = false;

    this.messageService.add({
      severity: 'success',
      summary: 'Erfolg',
      detail: 'Einladung wurde erfolgreich versendet.',
    });

    setTimeout(() => {
      this.loadSuperAdmins();
    }, 1000);
  }

  confirmDelete(admin: SuperAdminResponse): void {
    this.confirmationService.confirm({
      message: `Möchten Sie den Super-Admin ${admin.email} wirklich löschen?`,
      header: 'Bestätigung',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Löschen',
      rejectLabel: 'Abbrechen',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.deleteAdmin(admin);
      },
    });
  }

  private deleteAdmin(admin: SuperAdminResponse): void {
    this.adminService.deleteSuperAdmin(admin.zitadelId).subscribe({
      next: () => {
        console.log('Admin deleted successfully');
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Super-Admin wurde gelöscht.',
        });
        // Auch hier eine kurze Verzögerung für die Konsistenz
        setTimeout(() => this.loadSuperAdmins(), 1000);
      },
      error: (err) => {
        console.error('Failed to delete admin:', err);

        let errorMessage = 'Super-Admin konnte nicht gelöscht werden.';

        // Check for specific error message from backend
        if (
          err.status === 409 &&
          (err.error?.detail === 'LAST_SUPER_ADMIN_CANNOT_BE_DELETED' ||
            err.error?.title === 'LAST_SUPER_ADMIN_CANNOT_BE_DELETED')
        ) {
          errorMessage =
            'Der letzte Super-Admin kann nicht gelöscht werden. Es muss mindestens ein Administrator im System verbleiben.';
        }
    this.adminService.deleteSuperAdmin(admin.zitadelId).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Super-Admin wurde gelöscht.',
        });

        setTimeout(() => this.loadSuperAdmins(), 1000);
      },
      error: (err: HttpErrorResponse) => {
        let errorMessage = 'Super-Admin konnte nicht gelöscht werden.';

        if (
          err.status === 409 &&
          (err.error?.detail === 'LAST_SUPER_ADMIN_CANNOT_BE_DELETED' ||
            err.error?.title === 'LAST_SUPER_ADMIN_CANNOT_BE_DELETED')
        ) {
          errorMessage =
            'Der letzte Super-Admin kann nicht gelöscht werden. Es muss mindestens ein Administrator im System verbleiben.';
        }

        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: errorMessage,
        });
      },
    });
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: errorMessage,
        });
      },
    });
  }

  private mapOrganizationError(error: HttpErrorResponse, fallbackMessage: string): string {
    const errorCode =
      error.error?.detail ??
      error.error?.title ??
      error.error?.code ??
      error.error?.errorCode ??
      '';

    if (error.status === 401) {
      return 'Du bist nicht angemeldet. Bitte melde dich erneut an.';
    }

    if (error.status === 403) {
      return 'Du hast keine Berechtigung, Unternehmen zu verwalten.';
    }

    if (error.status === 404) {
      return 'Das Unternehmen wurde nicht gefunden.';
    }

    if (error.status === 502 && errorCode === 'PARTIAL_DELETION_FAILURE') {
      return 'Das Unternehmen konnte nicht vollständig gelöscht werden, weil mindestens ein Mitglied nicht entfernt werden konnte. Bitte versuche es später erneut.';
    }

    if (error.status === 502) {
      return 'Das Unternehmen konnte wegen eines Fehlers bei Zitadel nicht verarbeitet werden.';
    }

    return fallbackMessage;
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);

    return new Intl.DateTimeFormat('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  }
}
