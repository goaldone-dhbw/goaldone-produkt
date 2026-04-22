import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ButtonModule } from 'primeng/button';


import { catchError, finalize, of } from 'rxjs';
import { BasePopupComponent } from '../../../shared/base-popup/base-popup.component';
import { SuperAdminManagementService, SuperAdminResponse } from '../../../api';

@Component({
  selector: 'app-add-super-admin-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    InputTextModule,
    MessageModule,
    ButtonModule,
    BasePopupComponent,
  ],
  templateUrl: './add-super-admin-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddSuperAdminDialogComponent {
  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() added = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private adminService = inject(SuperAdminManagementService);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  loading = signal(false);
  errorMessage = signal<string | null>(null);
  invitationResult = signal<SuperAdminResponse | null>(null);

  close() {
    this.visibleChange.emit(false);
    this.form.reset();
    this.errorMessage.set(null);
    this.invitationResult.set(null);
  }

  submit() {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { email } = this.form.getRawValue();

    this.adminService
      .inviteSuperAdmin({
        email: email!,
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        catchError((error) => {
          const detail = error.error;
          if (error.status === 409 && detail?.detail === 'super-admin-invitation-already-exists') {
            this.errorMessage.set('Für diese E-Mail-Adresse existiert bereits eine offene Einladung oder ein Super-Admin-Account.');
          } else if (error.status === 400 && error.error?.errors) {
            const firstError = error.error.errors[0];
            this.errorMessage.set(firstError.message || 'Ungültige Eingabe.');
          } else {
            this.errorMessage.set('Fehler beim Einladen. Bitte versuche es erneut.');
          }
          return of(null);
        }),
      )
      .subscribe((res) => {
        if (res) {
          this.invitationResult.set(res);
          this.added.emit();
        }
      });
  }
}
