import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { finalize } from 'rxjs';

import { SuperAdminResponse, SuperAdminService } from '../../api';
import { AddSuperAdminDialogComponent } from './add-super-admin-dialog.component';
import { DeleteSuperAdminDialogComponent } from './delete-super-admin-dialog.component';

@Component({
  selector: 'app-super-admins-page',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    MessageModule,
    ProgressSpinnerModule,
    AddSuperAdminDialogComponent,
    DeleteSuperAdminDialogComponent,
  ],
  templateUrl: './super-admins-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperAdminsPageComponent {
  private superAdminService = inject(SuperAdminService);

  superAdmins = signal<SuperAdminResponse[]>([]);
  loading = signal(false);
  errorMessage = signal<string | null>(null);

  addDialogVisible = signal(false);
  adminToDelete = signal<SuperAdminResponse | null>(null);

  superAdminCount = computed(() => this.superAdmins().length);
  hasOnlyOneSuperAdmin = computed(() => this.superAdminCount() === 1);

  constructor() {
    this.loadSuperAdmins();
  }

  loadSuperAdmins(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.superAdminService
      .listSuperAdmins()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.superAdmins.set(response.superAdmins ?? []);
        },
        error: () => {
          this.errorMessage.set('Fehler beim Laden der Super-Admins.');
          this.superAdmins.set([]);
        },
      });
  }

  openAddDialog(): void {
    this.addDialogVisible.set(true);
  }

  onAdded(admin: SuperAdminResponse): void {
    this.addDialogVisible.set(false);
    this.superAdmins.update(admins => [...admins, admin]);
  }

  openDeleteDialog(admin: SuperAdminResponse): void {
    this.adminToDelete.set(admin);
  }

  onDeleted(userId: string): void {
    this.superAdmins.update(admins => admins.filter(a => a.userId !== userId));
    this.adminToDelete.set(null);
  }

  trackByUserId(_: number, admin: SuperAdminResponse): string {
    return admin.userId;
  }
}
