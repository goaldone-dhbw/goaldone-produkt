import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { forkJoin, firstValueFrom } from 'rxjs';
import { map } from 'rxjs/operators';

import {
  AccountResponse,
  Appointment,
  AppointmentCreate,
  AppointmentsService,
  DayOfWeek,
  UserAccountsService,
} from '../../api';

type AppointmentItem = Appointment & {
  accountId: string;
  accountLabel: string;
};

type AccountOption = {
  id: string;
  label: string;
};

type AppointmentFormType = 'ONCE' | 'RECURRING';

@Component({
  selector: 'app-appointments-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    SelectButtonModule,
    TooltipModule,
  ],
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
  recurrenceEndDate = '';
  startTime = '';
  endTime = '';
  appointmentType: AppointmentFormType = 'ONCE';
  selectedDays: DayOfWeek[] = [];
  editingAppointment: AppointmentItem | null = null;

  readonly appointmentTypeOptions = [
    { label: 'Einmalig', value: 'ONCE' },
    { label: 'Wiederkehrend', value: 'RECURRING' },
  ];

  readonly dayOptions = [
    { label: 'Mo', value: DayOfWeek.Monday },
    { label: 'Di', value: DayOfWeek.Tuesday },
    { label: 'Mi', value: DayOfWeek.Wednesday },
    { label: 'Do', value: DayOfWeek.Thursday },
    { label: 'Fr', value: DayOfWeek.Friday },
    { label: 'Sa', value: DayOfWeek.Saturday },
    { label: 'So', value: DayOfWeek.Sunday },
  ];

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
                accountId: account.id,
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
    this.editingAppointment = null;

    this.title = '';
    this.date = '';
    this.recurrenceEndDate = '';
    this.startTime = '';
    this.endTime = '';
    this.appointmentType = 'ONCE';
    this.selectedDays = [];
    this.selectedAccountId = this.accounts()[0]?.id ?? '';

    this.isDialogOpen.set(true);
  }

  openEditDialog(appointment: AppointmentItem): void {
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.successMessage.set('');

    this.editingAppointment = appointment;
    this.selectedAccountId = appointment.accountId;
    this.title = appointment.title ?? '';
    this.date = appointment.date ?? '';
    this.startTime = appointment.startTime ?? '';
    this.endTime = appointment.endTime ?? '';

    const recurring = this.isRecurringAppointment(appointment);

    this.appointmentType = recurring ? 'RECURRING' : 'ONCE';
    this.selectedDays = recurring ? this.parseDaysFromRrule(appointment.rrule) : [];
    this.recurrenceEndDate = recurring ? this.parseUntilDateFromRrule(appointment.rrule) : '';

    this.isDialogOpen.set(true);
  }

  closeDialog(): void {
    this.isDialogOpen.set(false);
    this.validationMessage.set('');
    this.editingAppointment = null;
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
      appointmentType: this.appointmentType === 'ONCE' ? 'ONE_TIME' : 'RECURRING',
      date: this.date,
      startTime: this.startTime,
      endTime: this.endTime,
      rrule:
        this.appointmentType === 'RECURRING'
          ? this.buildWeeklyRrule(this.selectedDays, this.recurrenceEndDate)
          : null,
    };

    this.isSaving.set(true);

    try {
      if (this.editingAppointment) {
        if (!this.editingAppointment.id) {
          this.errorMessage.set(
            'Der Termin kann nicht bearbeitet werden, weil keine ID vorhanden ist.',
          );
          return;
        }

        await firstValueFrom(
          this.appointmentsService.updateAppointment(
            this.editingAppointment.accountId,
            this.editingAppointment.id,
            payload,
          ),
        );

        this.successMessage.set('Der Termin wurde aktualisiert.');
      } else {
        await firstValueFrom(
          this.appointmentsService.createAppointment(this.selectedAccountId, payload),
        );

        this.successMessage.set('Der Termin wurde gespeichert.');
      }

      this.isDialogOpen.set(false);
      this.editingAppointment = null;
      await this.loadAppointments();
    } catch (error) {
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Der Termin konnte nicht gespeichert werden.'),
      );
    } finally {
      this.isSaving.set(false);
    }
  }

  async deleteAppointment(appointment: AppointmentItem): Promise<void> {
    if (!appointment.id) {
      this.errorMessage.set('Der Termin kann nicht gelöscht werden, weil keine ID vorhanden ist.');
      return;
    }

    const confirmed = window.confirm(
      `Soll der Termin "${appointment.title ?? 'Termin'}" wirklich gelöscht werden?`,
    );

    if (!confirmed) {
      return;
    }

    this.errorMessage.set('');
    this.successMessage.set('');

    try {
      await firstValueFrom(
        this.appointmentsService.deleteAppointment(appointment.accountId, appointment.id),
      );

      this.successMessage.set('Der Termin wurde gelöscht.');
      await this.loadAppointments();
    } catch (error) {
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Der Termin konnte nicht gelöscht werden.'),
      );
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

  formatAppointmentType(appointment: Appointment): string {
    return this.isRecurringAppointment(appointment) ? 'Wiederkehrend' : 'Einmalig';
  }

  formatAppointmentDateOrDays(appointment: Appointment): string {
    if (this.isRecurringAppointment(appointment)) {
      const days = this.parseDaysFromRrule(appointment.rrule);
      return days.length ? this.formatDays(days) : 'Wiederkehrend';
    }

    return this.formatDate(appointment.date);
  }

  getRecurringEndDate(appointment: Appointment): string {
    return this.parseUntilDateFromRrule(appointment.rrule);
  }

  isRecurringAppointment(appointment: Appointment): boolean {
    return appointment.appointmentType === 'RECURRING' || Boolean(appointment.rrule);
  }

  private validateForm(): string | null {
    if (!this.selectedAccountId) {
      return 'Bitte wähle ein Unternehmen oder einen Verein aus.';
    }

    if (!this.title.trim()) {
      return 'Bitte gib einen Titel ein.';
    }

    if (this.appointmentType !== 'ONCE' && this.appointmentType !== 'RECURRING') {
      return 'Bitte wähle aus, ob der Termin einmalig oder wiederkehrend ist.';
    }

    if (!this.date) {
      return this.appointmentType === 'RECURRING'
        ? 'Bitte gib ein Startdatum ein.'
        : 'Bitte gib ein Datum ein.';
    }

    if (this.appointmentType === 'RECURRING' && !this.recurrenceEndDate) {
      return 'Bitte gib ein Enddatum ein.';
    }

    if (this.appointmentType === 'RECURRING' && this.recurrenceEndDate < this.date) {
      return 'Das Enddatum darf nicht vor dem Startdatum liegen.';
    }

    if (this.appointmentType === 'RECURRING' && this.selectedDays.length === 0) {
      return 'Bitte wähle mindestens einen Wochentag aus.';
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
    const recurringPrefix = this.isRecurringAppointment(appointment) ? '1' : '0';
    return `${recurringPrefix}-${appointment.date ?? '0000-01-01'}T${
      appointment.startTime ?? ''
    }`;
  }

  private buildWeeklyRrule(days: DayOfWeek[], untilDate?: string): string {
    const dayCodes: Record<string, string> = {
      MONDAY: 'MO',
      TUESDAY: 'TU',
      WEDNESDAY: 'WE',
      THURSDAY: 'TH',
      FRIDAY: 'FR',
      SATURDAY: 'SA',
      SUNDAY: 'SU',
    };

    const order = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

    const byDay = days
      .slice()
      .sort((a, b) => order.indexOf(a.toUpperCase()) - order.indexOf(b.toUpperCase()))
      .map((day) => dayCodes[day.toUpperCase()])
      .filter(Boolean)
      .join(',');

    const parts = [`FREQ=WEEKLY`, `BYDAY=${byDay}`];

    if (untilDate) {
      parts.push(`UNTIL=${untilDate.replaceAll('-', '')}`);
    }

    return parts.join(';');
  }

  private parseDaysFromRrule(rrule?: string | null): DayOfWeek[] {
    if (!rrule) {
      return [];
    }

    const codeToDay: Record<string, DayOfWeek> = {
      MO: DayOfWeek.Monday,
      TU: DayOfWeek.Tuesday,
      WE: DayOfWeek.Wednesday,
      TH: DayOfWeek.Thursday,
      FR: DayOfWeek.Friday,
      SA: DayOfWeek.Saturday,
      SU: DayOfWeek.Sunday,
    };

    const byDay = rrule
      .toUpperCase()
      .split(';')
      .find((part) => part.startsWith('BYDAY='))
      ?.substring('BYDAY='.length);

    if (!byDay) {
      return [];
    }

    return byDay
      .split(',')
      .map((code) => codeToDay[code])
      .filter((day): day is DayOfWeek => !!day);
  }

  private parseUntilDateFromRrule(rrule?: string | null): string {
    if (!rrule) {
      return '';
    }

    const until = rrule
      .toUpperCase()
      .split(';')
      .find((part) => part.startsWith('UNTIL='))
      ?.substring('UNTIL='.length);

    if (!until) {
      return '';
    }

    const normalized = until.replace('Z', '').split('T')[0];

    if (/^\d{8}$/.test(normalized)) {
      return `${normalized.substring(0, 4)}-${normalized.substring(4, 6)}-${normalized.substring(
        6,
        8,
      )}`;
    }

    if (/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
      return normalized;
    }

    return '';
  }

  private formatDays(days: DayOfWeek[]): string {
    const labels: Record<string, string> = {
      MONDAY: 'Mo',
      TUESDAY: 'Di',
      WEDNESDAY: 'Mi',
      THURSDAY: 'Do',
      FRIDAY: 'Fr',
      SATURDAY: 'Sa',
      SUNDAY: 'So',
    };

    return days.map((day) => labels[day.toUpperCase()] ?? day).join(', ');
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
