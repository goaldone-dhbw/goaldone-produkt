import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { finalize, catchError, of } from 'rxjs';
import { SuperAdminResponse, SuperAdminService } from '../../api';
import { BasePopupComponent } from '../../shared/base-popup/base-popup.component';


@Component({
  selector: 'app-delete-super-admin-dialog',
  standalone: true,
  imports: [CommonModule, ButtonModule, MessageModule, BasePopupComponent],
  templateUrl: './delete-super-admin-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeleteSuperAdminDialogComponent {
  @Input({ required: true }) admin!: SuperAdminResponse;
  @Output() deleted = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  private superAdminService = inject(SuperAdminService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);

  confirm(): void {
    if (this.loading()) return;

    this.loading.set(true);
    this.errorMessage.set(null);

    this.superAdminService
      .deleteSuperAdmin(this.admin.userId)
      .pipe(
        finalize(() => this.loading.set(false)),
        catchError(() => {
          this.errorMessage.set('Fehler beim Löschen. Bitte versuche es erneut.');
          return of(null);
        }),
      )
      .subscribe((res) => {
        if (res !== null) {
          this.deleted.emit(this.admin.userId);
        }
      });
  }

  cancel(): void {
    if (this.loading()) return;
    this.cancelled.emit();
  }
}
