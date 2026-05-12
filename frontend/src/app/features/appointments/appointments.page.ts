import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { forkJoin, firstValueFrom } from 'rxjs';
import { map } from 'rxjs/operators';

import {
  AccountResponse,
  Appointment,
  AppointmentCreate,
  AppointmentsService,
  UserAccountsService,
} from '../../api';

type AppointmentItem = Appointment & {
  accountLabel: string;
};

type AccountOption = {
  id: string;
  label: string;
};

@Component({
  selector: 'app-appointments-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, DialogModule, InputTextModule, SelectModule],
  templateUrl: './appointments.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppointmentsPage {
  private readonly appointmentsService = inject(AppointmentsService);
  private readonly userAccountsService = inject(UserAccountsService);

  readonly accounts = signal<AccountOption[]>([]);
  readonly appointments = signal<AppointmentItem[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isDialogOpen = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly validationMessage = signal('');

  readonly hasAccounts = computed(() => this.accounts().length > 0);
  readonly hasMultipleAccounts = computed(() => this.accounts().length > 1);

  selectedAccountId = '';
  title = '';
  date = '';
  startTime = '';
  endTime = '';

  constructor() {
    void this.initializePage();
  }

  private async initializePage(): Promise<void> {
    await this.loadAccounts();
    await this.loadAppointments();
  }

  async loadAccounts(): Promise<void> {
    try {
      const response = await firstValueFrom(this.userAccountsService.getMyAccounts());
      const accounts = this.mapAccounts(response.accounts ?? []);

      this.accounts.set(accounts);
      this.selectedAccountId = accounts[0]?.id ?? '';
    } catch (error) {
      this.accounts.set([]);
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Accounts konnten nicht geladen werden.'),
      );
    }
  }

  async loadAppointments(): Promise<void> {
    const accounts = this.accounts();

    if (accounts.length === 0) {
      this.appointments.set([]);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    const requests = accounts.map((account) =>
      this.appointmentsService.listAppointments(account.id).pipe(
        map((response) =>
          (response.appointments ?? [])
            .filter((appointment) => !appointment.isBreak)
            .map(
              (appointment): AppointmentItem => ({
                ...appointment,
                accountLabel: account.label,
              }),
            ),
        ),
      ),
    );

    try {
      const appointmentsByAccount = await firstValueFrom(forkJoin(requests));
      const appointments = appointmentsByAccount
        .flat()
        .sort((left, right) =>
          this.getAppointmentSortValue(left).localeCompare(this.getAppointmentSortValue(right)),
        );

      this.appointments.set(appointments);
    } catch (error) {
      this.appointments.set([]);
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Termine konnten nicht geladen werden.'),
      );
    } finally {
      this.isLoading.set(false);
    }
  }

  openCreateDialog(): void {
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');
    this.title = '';
    this.date = '';
    this.startTime = '';
    this.endTime = '';
    this.selectedAccountId = this.accounts()[0]?.id ?? '';
    this.isDialogOpen.set(true);
  }

  async saveAppointment(): Promise<void> {
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');

    const validationError = this.validateForm();

    if (validationError) {
      this.validationMessage.set(validationError);
      return;
    }

    const payload: AppointmentCreate = {
      title: this.title.trim(),
      isBreak: false,
      appointmentType: 'ONE_TIME',
      date: this.date,
      startTime: this.startTime,
      endTime: this.endTime,
      rrule: null,
    };

    this.isSaving.set(true);

    try {
      await firstValueFrom(
        this.appointmentsService.createAppointment(this.selectedAccountId, payload),
      );
      this.successMessage.set('Der Termin wurde gespeichert.');
      this.isDialogOpen.set(false);
      await this.loadAppointments();
    } catch (error) {
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Der Termin konnte nicht gespeichert werden.'),
      );
    } finally {
      this.isSaving.set(false);
    }
  }

  getAccountLabel(accountId: string): string {
    return this.accounts().find((account) => account.id === accountId)?.label ?? 'Unbekannt';
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '-';
    }

    const date = new Date(`${value}T00:00:00`);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
  }

  private validateForm(): string | null {
    if (!this.selectedAccountId) {
      return 'Bitte wähle ein Unternehmen oder einen Verein aus.';
    }

    if (!this.title.trim()) {
      return 'Bitte gib einen Titel ein.';
    }

    if (!this.date) {
      return 'Bitte gib ein Datum ein.';
    }

    if (!this.startTime) {
      return 'Bitte gib eine Startzeit ein.';
    }

    if (!this.endTime) {
      return 'Bitte gib eine Endzeit ein.';
    }

    if (this.endTime <= this.startTime) {
      return 'Die Endzeit muss nach der Startzeit liegen.';
    }

    return null;
  }

  private mapAccounts(accounts: AccountResponse[]): AccountOption[] {
    return accounts
      .filter((account) => !!account.accountId)
      .map((account) => ({
        id: account.accountId.toString(),
        label: account.organizationName || 'Unbenannte Organisation',
      }));
  }

  private getAppointmentSortValue(appointment: AppointmentItem): string {
    return `${appointment.date ?? '9999-12-31'}T${appointment.startTime}`;
  }

  private getReadableErrorMessage(error: unknown, fallback: string): string {
    const responseError =
      error instanceof HttpErrorResponse
        ? error.error
        : typeof error === 'object' && error !== null && 'error' in error
          ? (error as { error?: unknown }).error
          : null;

    if (typeof responseError === 'string' && responseError.trim()) {
      return responseError;
    }

    if (typeof responseError === 'object' && responseError !== null) {
      const body = responseError as { message?: string; detail?: string; error?: string };
      return body.message || body.detail || body.error || fallback;
    }

    return fallback;
  }
}
