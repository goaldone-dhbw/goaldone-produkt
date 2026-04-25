import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';
import { ConfirmationService, MessageService } from 'primeng/api';
import { SuperAdminManagementService, SuperAdminResponse } from '../../api';
import { AddSuperAdminDialogComponent } from './dialog/add-super-admin-dialog.component';
import { finalize } from 'rxjs';

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
  ],
  templateUrl: './super-admins-page.component.html',
  providers: [ConfirmationService, MessageService]
})
export class SuperAdminsPageComponent implements OnInit {
  private readonly adminService = inject(SuperAdminManagementService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);

  readonly superAdmins = signal<SuperAdminResponse[]>([]);
  readonly isLoading = signal(false);
  addDialogVisible = false;

  ngOnInit(): void {
    this.loadSuperAdmins();
  }

  loadSuperAdmins(): void {
    this.isLoading.set(true);
    this.adminService.listSuperAdmins()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (admins) => this.superAdmins.set(admins),
        error: () => this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Super-Admins konnten nicht geladen werden.'
        })
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
    this.loadSuperAdmins();
  }

  confirmDelete(admin: SuperAdminResponse): void {
    this.confirmationService.confirm({
      message: `Möchten Sie den Super-Admin ${admin.email} wirklich löschen?`,
      header: 'Bestätigung',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Löschen',
      rejectLabel: 'Abbrechen',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteAdmin(admin)
    });
  }

  private deleteAdmin(admin: SuperAdminResponse): void {
    this.adminService.deleteSuperAdmin(admin.zitadelId)
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Erfolg',
            detail: 'Super-Admin wurde gelöscht.'
          });
          this.loadSuperAdmins();
        },
        error: () => this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Super-Admin konnte nicht gelöscht werden.'
        })
      });
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
