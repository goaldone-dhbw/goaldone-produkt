import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import { finalize } from 'rxjs';

import {
  CreateOrganizationRequest,
  OrgManagementService,
  OrganizationResponse,
} from '../../../../api';

type CreateOrganizationControlName = 'name' | 'adminFirstName' | 'adminLastName' | 'adminEmail';

function trimmedLengthValidator(min: number, max: number): ValidatorFn {
  return (control: AbstractControl<string | null>): ValidationErrors | null => {
    const value = control.value?.trim() ?? '';

    if (!value) {
      return { required: true };
    }

    if (value.length < min) {
      return {
        minlength: {
          requiredLength: min,
          actualLength: value.length,
        },
      };
    }

    if (value.length > max) {
      return {
        maxlength: {
          requiredLength: max,
          actualLength: value.length,
        },
      };
    }

    return null;
  };
}

@Component({
  selector: 'app-create-organization-card',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ButtonModule, InputTextModule],
  templateUrl: './create-organization-card.html',
})
export class CreateOrganizationCardComponent {
  private readonly fb = inject(FormBuilder);
  private readonly organizationService = inject(OrgManagementService);
  private readonly messageService = inject(MessageService);

  @Output() readonly created = new EventEmitter<OrganizationResponse>();

  readonly isSubmitting = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [trimmedLengthValidator(2, 255)]],
    adminFirstName: ['', [trimmedLengthValidator(1, 255)]],
    adminLastName: ['', [trimmedLengthValidator(1, 255)]],
    adminEmail: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    this.successMessage.set(null);
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request: CreateOrganizationRequest = {
      name: this.form.controls.name.value.trim(),
      adminFirstName: this.form.controls.adminFirstName.value.trim(),
      adminLastName: this.form.controls.adminLastName.value.trim(),
      adminEmail: this.form.controls.adminEmail.value.trim(),
    };

    this.isSubmitting.set(true);

    this.organizationService
      .createOrganization(request)
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: (organization) => {
          const adminEmail = organization.adminEmail ?? request.adminEmail;

          this.successMessage.set(
            `Das Unternehmen wurde erfolgreich angelegt. Die Einladung wurde an ${adminEmail} versendet.`,
          );

          this.messageService.add({
            severity: 'success',
            summary: 'Unternehmen angelegt',
            detail: `Die Einladung wurde an ${adminEmail} versendet.`,
          });

          this.form.reset();
          this.created.emit(organization);
        },
        error: (error: HttpErrorResponse) => {
          const message = this.mapApiError(error);
          this.errorMessage.set(message);

          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail: message,
          });
        },
      });
  }

  isInvalid(controlName: CreateOrganizationControlName): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.dirty || control.touched);
  }

  getNameError(): string {
    const control = this.form.controls.name;

    if (control.hasError('required')) {
      return 'Bitte gib einen Unternehmensnamen ein.';
    }

    if (control.hasError('minlength')) {
      return 'Der Unternehmensname muss mindestens 2 Zeichen lang sein.';
    }

    if (control.hasError('maxlength')) {
      return 'Der Unternehmensname darf maximal 255 Zeichen lang sein.';
    }

    return '';
  }

  getAdminFirstNameError(): string {
    const control = this.form.controls.adminFirstName;

    if (control.hasError('required')) {
      return 'Bitte gib den Vornamen des ersten Unternehmens-Admins ein.';
    }

    if (control.hasError('maxlength')) {
      return 'Der Vorname darf maximal 255 Zeichen lang sein.';
    }

    return '';
  }

  getAdminLastNameError(): string {
    const control = this.form.controls.adminLastName;

    if (control.hasError('required')) {
      return 'Bitte gib den Nachnamen des ersten Unternehmens-Admins ein.';
    }

    if (control.hasError('maxlength')) {
      return 'Der Nachname darf maximal 255 Zeichen lang sein.';
    }

    return '';
  }

  getAdminEmailError(): string {
    const control = this.form.controls.adminEmail;

    if (control.hasError('required')) {
      return 'Bitte gib die E-Mail-Adresse des ersten Unternehmens-Admins ein.';
    }

    if (control.hasError('email')) {
      return 'Bitte gib eine gültige E-Mail-Adresse ein.';
    }

    return '';
  }

  private mapApiError(error: HttpErrorResponse): string {
    const errorCode =
      error.error?.detail ??
      error.error?.title ??
      error.error?.code ??
      error.error?.errorCode ??
      '';

    if (error.status === 409 && errorCode === 'EMAIL_ALREADY_IN_USE') {
      return 'Diese E-Mail ist bereits einem anderen Account zugeordnet.';
    }

    if (error.status === 409 && errorCode === 'ORGANIZATION_NAME_ALREADY_EXISTS') {
      return 'Dieser Unternehmensname ist bereits vergeben.';
    }

    if (error.status === 400) {
      return 'Bitte prüfe die Eingaben. Mindestens ein Feld ist ungültig.';
    }

    if (error.status === 401) {
      return 'Du bist nicht angemeldet. Bitte melde dich erneut an.';
    }

    if (error.status === 403) {
      return 'Du hast keine Berechtigung, Unternehmen anzulegen.';
    }

    if (error.status === 502) {
      return 'Das Unternehmen konnte wegen eines Fehlers bei Zitadel nicht angelegt werden. Es wurden keine unvollständigen Daten gespeichert.';
    }

    return 'Das Unternehmen konnte nicht angelegt werden. Bitte versuche es später erneut.';
  }
}
