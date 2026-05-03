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

/**
 * Names of all form controls used in the create organization form.
 * This type is used to access form controls in a type-safe way.
 */
type CreateOrganizationControlName = 'name' | 'adminFirstName' | 'adminLastName' | 'adminEmail';

/**
 * Creates a validator that checks whether a text input is not empty after trimming
 * and whether its length is within the given minimum and maximum range.
 *
 * @param min Minimum allowed length after trimming whitespace.
 * @param max Maximum allowed length after trimming whitespace.
 * @returns A validator function for Angular reactive forms.
 */
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
/**
 * Component for creating a new organization from the super-admin area.
 *
 * The component provides a reactive form for entering the organization name
 * and the first organization admin's personal data. It validates all fields
 * on the client side and sends a create request to the backend only if the
 * form is valid.
 *
 * After a successful request, the component shows a success message and emits
 * the created organization response to the parent component. API errors are
 * mapped to user-friendly German error messages.
 */
export class CreateOrganizationCardComponent {
  private readonly fb = inject(FormBuilder);
  private readonly organizationService = inject(OrgManagementService);
  private readonly messageService = inject(MessageService);

  /**
   * Emits the created organization after a successful API request.
   * Parent components can use this event to refresh organization lists.
   */
  @Output() readonly created = new EventEmitter<OrganizationResponse>();

  /**
   * Indicates whether the create request is currently running.
   * Used to show the loading state and prevent duplicate submissions.
   */
  readonly isSubmitting = signal(false);

  /**
   * Contains the success message shown after an organization was created.
   */
  readonly successMessage = signal<string | null>(null);

  /**
   * Contains the user-friendly error message shown after failed requests.
   */
  readonly errorMessage = signal<string | null>(null);

  /**
   * Reactive form for creating an organization and inviting the first admin.
   *
   * Validates:
   * - organization name: required, 2 to 255 characters
   * - admin first name: required, 1 to 255 characters
   * - admin last name: required, 1 to 255 characters
   * - admin email: required, valid email format
   */
  readonly form = this.fb.nonNullable.group({
    name: ['', [trimmedLengthValidator(2, 255)]],
    adminFirstName: ['', [trimmedLengthValidator(1, 255)]],
    adminLastName: ['', [trimmedLengthValidator(1, 255)]],
    adminEmail: ['', [Validators.required, Validators.email]],
  });

  /**
   * Submits the create organization form.
   *
   * If the form is invalid, all controls are marked as touched and no API request
   * is sent. If the form is valid, the input values are trimmed and sent to the
   * backend via the generated OpenAPI service.
   *
   * On success, the form is reset, a success message is shown and the created
   * organization is emitted. On error, the API error is mapped to a readable
   * message for the user.
   */
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
        next: (organization: OrganizationResponse) => {
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

  /**
   * Checks whether a specific form control is invalid and should display an error.
   *
   * @param controlName Name of the form control to check.
   * @returns True if the control is invalid and was touched or changed.
   */
  isInvalid(controlName: CreateOrganizationControlName): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.dirty || control.touched);
  }

  /**
   * Returns the validation error message for the organization name field.
   *
   * @returns A German validation message or an empty string if there is no error.
   */
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

  /**
   * Returns the validation error message for the admin first name field.
   *
   * @returns A German validation message or an empty string if there is no error.
   */
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

  /**
   * Returns the validation error message for the admin last name field.
   *
   * @returns A German validation message or an empty string if there is no error.
   */
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

  /**
   * Returns the validation error message for the admin email field.
   *
   * @returns A German validation message or an empty string if there is no error.
   */
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

  /**
   * Maps backend error responses to user-friendly German error messages.
   *
   * Handles known error codes such as EMAIL_ALREADY_IN_USE and
   * ORGANIZATION_NAME_ALREADY_EXISTS, as well as common HTTP status codes.
   *
   * @param error The HTTP error returned by the backend.
   * @returns A readable German error message for the UI.
   */
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
