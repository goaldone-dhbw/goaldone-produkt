import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize } from 'rxjs';

import {
  OrgManagementService,
  SuperAdminManagementService,
  SuperAdminResponse,
} from '../../api';

type OrganizationListItem = {
  id?: string | null;
  zitadelOrganizationId: string;
  name: string;
  createdAt: string;
};

import { AddSuperAdminDialogComponent } from './dialog/add-super-admin-dialog.component';
import { CreateOrganizationCardComponent } from './create-organization/create-organization-card/create-organization-card';

/**
 * Page component for the super-admin administration area.
 *
 * This page combines two administrative use cases:
 * - managing super-admin users
 * - managing organizations
 *
 * Super-admins can view organizations, create new organizations through the
 * create organization card, and delete existing organizations after confirmation.
 */
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

  /**
   * List of currently loaded super-admin users.
   */
  readonly superAdmins = signal<SuperAdminResponse[]>([]);

  /**
   * List of currently loaded organizations.
   */
  readonly organizations = signal<OrganizationListItem[]>([]);

  /**
   * Loading state for the super-admin list.
   */
  readonly isLoading = signal(false);

  /**
   * Loading state for the organization list.
   */
  readonly isOrganizationsLoading = signal(false);

  /**
   * Set of Zitadel organization IDs that are currently being deleted.
   *
   * A set is used so that multiple delete operations can be tracked independently.
   */
  readonly deletingOrganizationIds = signal<Set<string>>(new Set());

  /**
   * Set of Zitadel user IDs that are currently being deleted.
   *
   * This prevents duplicate delete requests for the same super-admin.
   */
  readonly deletingSuperAdminIds = signal<Set<string>>(new Set());

  /**
   * Controls the visibility of the add-super-admin dialog.
   */
  addDialogVisible = false;

  /**
   * Initializes the page data.
   *
   * Both super-admins and organizations are loaded when the component is created.
   */
  ngOnInit(): void {
    this.loadSuperAdmins();
    this.loadOrganizations();
  }

  /**
   * Loads all super-admin users from the backend.
   *
   * While the request is running, the super-admin loading state is active.
   * If the request fails, an error toast is shown.
   */
  loadSuperAdmins(): void {
    this.isLoading.set(true);

    this.adminService
      .listSuperAdmins()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (admins: SuperAdminResponse[]) => {
          this.superAdmins.set(admins);
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: 'Super-Admins konnten nicht geladen werden.',
          });
        },
      });
  }

  /**
   * Loads all organizations from the backend.
   *
   * The backend uses Zitadel as source of truth and returns all organizations
   * that are visible to the current super-admin.
   */
  loadOrganizations(): void {
    this.isOrganizationsLoading.set(true);

    this.orgManagementService
      .listOrganizations()
      .pipe(finalize(() => this.isOrganizationsLoading.set(false)))
      .subscribe({
        next: (response) => {
          this.organizations.set(response.organizations ?? []);
        },
        error: (error: HttpErrorResponse) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: this.mapOrganizationError(error, 'Unternehmen konnten nicht geladen werden.'),
          });
        },
      });
  }

  /**
   * Handles the successful creation of an organization.
   *
   * This method is called by the create organization card after a successful
   * POST request. The organization list is reloaded so the new organization
   * appears in the overview.
   */
  onOrganizationCreated(): void {
    this.messageService.add({
      severity: 'success',
      summary: 'Unternehmen angelegt',
      detail: 'Das Unternehmen wurde erfolgreich angelegt.',
    });

    this.loadOrganizations();
  }

  /**
   * Opens a confirmation dialog before deleting an organization.
   *
   * The actual DELETE request is only triggered after the user confirms the
   * dialog. This fulfills the ticket requirement that deletion must be confirmed.
   *
   * @param organization The organization that should be deleted.
   */
  confirmDeleteOrganization(organization: OrganizationListItem): void {
    this.confirmationService.confirm({
      message: `Möchtest du das Unternehmen "${organization.name}" wirklich löschen? Alle zugehörigen Mitglieder und Daten werden entfernt.`,
      header: 'Unternehmen löschen',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Löschen',
      rejectLabel: 'Abbrechen',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteOrganization(organization),
    });
  }

  /**
   * Checks whether a specific organization is currently being deleted.
   *
   * @param zitadelOrganizationId The Zitadel organization ID.
   * @returns true if the organization is currently being deleted, otherwise false.
   */
  isDeletingOrganization(zitadelOrganizationId: string | undefined | null): boolean {
    if (!zitadelOrganizationId) {
      return false;
    }

    return this.deletingOrganizationIds().has(zitadelOrganizationId);
  }

  /**
   * Deletes an organization by its Zitadel organization ID.
   *
   * Important:
   * The backend endpoint expects the Zitadel organization ID, not the local DB ID.
   * Therefore `organization.zitadelOrganizationId` must be used for the DELETE request.
   *
   * On success, the organization is removed from the local signal state.
   * On failure, the organization remains visible and an error toast is shown.
   *
   * @param organization The organization that should be deleted.
   */
  private deleteOrganization(organization: OrganizationListItem): void {
    const zitadelOrganizationId = organization.zitadelOrganizationId;

    if (!zitadelOrganizationId) {
      this.messageService.add({
        severity: 'error',
        summary: 'Fehler',
        detail:
          'Das Unternehmen kann nicht gelöscht werden, weil keine Zitadel-Organisations-ID vorhanden ist.',
      });
      return;
    }

    if (this.isDeletingOrganization(zitadelOrganizationId)) {
      return;
    }

    this.addDeletingOrganizationId(zitadelOrganizationId);

    this.orgManagementService
      .deleteOrganization(zitadelOrganizationId)
      .pipe(finalize(() => this.removeDeletingOrganizationId(zitadelOrganizationId)))
      .subscribe({
        next: () => {
          this.organizations.update((organizations) =>
            organizations.filter((item) => item.zitadelOrganizationId !== zitadelOrganizationId),
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

  /**
   * Marks an organization as currently being deleted.
   *
   * This is used to show a loading state and prevent duplicate delete requests.
   *
   * @param zitadelOrganizationId The Zitadel organization ID.
   */
  private addDeletingOrganizationId(zitadelOrganizationId: string): void {
    this.deletingOrganizationIds.update((ids) => {
      const nextIds = new Set(ids);
      nextIds.add(zitadelOrganizationId);
      return nextIds;
    });
  }

  /**
   * Removes an organization from the deleting state.
   *
   * @param zitadelOrganizationId The Zitadel organization ID.
   */
  private removeDeletingOrganizationId(zitadelOrganizationId: string): void {
    this.deletingOrganizationIds.update((ids) => {
      const nextIds = new Set(ids);
      nextIds.delete(zitadelOrganizationId);
      return nextIds;
    });
  }

  /**
   * Opens the dialog for inviting a new super-admin.
   */
  openAddDialog(): void {
    this.addDialogVisible = true;
  }

  /**
   * Updates the visibility state of the add-super-admin dialog.
   *
   * @param value true if the dialog should be visible, otherwise false.
   */
  onDialogVisibleChange(value: boolean): void {
    this.addDialogVisible = value;
  }

  /**
   * Handles successful creation of a super-admin invitation.
   *
   * The dialog is closed, a success toast is shown, and the super-admin list is reloaded.
   */
  onAdded(): void {
    this.addDialogVisible = false;

    this.messageService.add({
      severity: 'success',
      summary: 'Erfolg',
      detail: 'Einladung wurde erfolgreich versendet.',
    });

    this.loadSuperAdmins();
  }

  /**
   * Opens a confirmation dialog before deleting a super-admin.
   *
   * @param admin The super-admin that should be deleted.
   */
  confirmDelete(admin: SuperAdminResponse): void {
    this.confirmationService.confirm({
      message: `Möchtest du den Super-Admin ${admin.email} wirklich löschen?`,
      header: 'Bestätigung',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Löschen',
      rejectLabel: 'Abbrechen',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteAdmin(admin),
    });
  }

  /**
   * Checks whether a specific super-admin is currently being deleted.
   *
   * @param zitadelId The Zitadel user ID of the super-admin.
   * @returns true if the super-admin is currently being deleted, otherwise false.
   */
  isDeletingSuperAdmin(zitadelId: string | undefined | null): boolean {
    if (!zitadelId) {
      return false;
    }

    return this.deletingSuperAdminIds().has(zitadelId);
  }

  /**
   * Deletes a super-admin user.
   *
   * The method prevents duplicate delete requests and maps the specific
   * LAST_SUPER_ADMIN error to a user-friendly message.
   *
   * @param admin The super-admin that should be deleted.
   */
  private deleteAdmin(admin: SuperAdminResponse): void {
    const zitadelId = admin.zitadelId;

    if (!zitadelId || this.isDeletingSuperAdmin(zitadelId)) {
      return;
    }

    this.addDeletingSuperAdminId(zitadelId);

    this.adminService
      .deleteSuperAdmin(zitadelId)
      .pipe(finalize(() => this.removeDeletingSuperAdminId(zitadelId)))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Erfolg',
            detail: 'Super-Admin wurde gelöscht.',
          });

          this.loadSuperAdmins();
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
  }

  /**
   * Marks a super-admin as currently being deleted.
   *
   * @param zitadelId The Zitadel user ID of the super-admin.
   */
  private addDeletingSuperAdminId(zitadelId: string): void {
    this.deletingSuperAdminIds.update((ids) => {
      const nextIds = new Set(ids);
      nextIds.add(zitadelId);
      return nextIds;
    });
  }

  /**
   * Removes a super-admin from the deleting state.
   *
   * @param zitadelId The Zitadel user ID of the super-admin.
   */
  private removeDeletingSuperAdminId(zitadelId: string): void {
    this.deletingSuperAdminIds.update((ids) => {
      const nextIds = new Set(ids);
      nextIds.delete(zitadelId);
      return nextIds;
    });
  }

  /**
   * Maps backend errors from organization management endpoints to user-friendly messages.
   *
   * This includes special handling for partial deletion failures. In that case,
   * the backend keeps the organization and the frontend informs the super-admin
   * that not all members could be deleted.
   *
   * @param error The HTTP error returned by the backend.
   * @param fallbackMessage Message used when no specific mapping exists.
   * @returns A translated and user-friendly error message.
   */
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

    if (error.status === 409) {
      return 'Das Unternehmen konnte wegen eines Konflikts nicht verarbeitet werden.';
    }

    if (error.status === 502 && errorCode === 'PARTIAL_DELETION_FAILURE') {
      return 'Das Unternehmen konnte nicht vollständig gelöscht werden, weil mindestens ein Mitglied nicht entfernt werden konnte. Das Unternehmen bleibt bestehen.';
    }

    if (error.status === 502) {
      return 'Das Unternehmen konnte wegen eines Fehlers bei Zitadel nicht verarbeitet werden.';
    }

    return fallbackMessage;
  }

  /**
   * Formats an ISO date string for display in the German date format.
   *
   * If the given value is missing or invalid, a dash is returned.
   *
   * @param dateStr ISO date string.
   * @returns Formatted German date string or "-".
   */
  formatDate(dateStr: string | undefined | null): string {
    if (!dateStr) {
      return '-';
    }

    const date = new Date(dateStr);

    if (Number.isNaN(date.getTime())) {
      return '-';
    }

    return new Intl.DateTimeFormat('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  }
}
