import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Tooltip } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';

import {
  WorkingTimesService,
  UserAccountsService,
  AppointmentsService,
  WorkingTimeResponse,
  AccountResponse,
  WorkingTimeCreateRequest,
  WorkingTimeUpdateRequest,
  DayOfWeek,
  Appointment,
  AppointmentCreate,
} from '../../api';

import { AccountStateService } from '../../core/services/account-state.service';

type BreakFormType = 'ONCE' | 'RECURRING';

@Component({
  selector: 'app-working-hours',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    ConfirmDialogModule,
    TagModule,
    ToastModule,
    SelectButtonModule,
    Tooltip,
  ],
  templateUrl: './working-hours.page.html',
  providers: [ConfirmationService, MessageService],
})
export class WorkingHoursPage implements OnInit {
  workingTimes = signal<WorkingTimeResponse[]>([]);
  breaks = signal<Appointment[]>([]);
  accounts = signal<AccountResponse[]>([]);
  loading = signal(false);
  breaksLoading = signal(false);

  showDialog = signal(false);
  isEditMode = signal(false);

  showBreakDialog = signal(false);
  isBreakEditMode = signal(false);

  selectedDays: DayOfWeek[] = [];
  selectedAccountId: string | null = null;
  editingId: string | null = null;
  startTimeString = '';
  endTimeString = '';

  editingBreakId: string | null = null;
  editingBreakAccountId: string | null = null;

  breakTitle = '';
  breakDate = '';
  breakStartTime = '';
  breakEndTime = '';
  breakType: BreakFormType = 'ONCE';
  breakSelectedDays: DayOfWeek[] = [];
  breakSelectedAccountId: string | null = null;

  readonly breakTypeOptions = [
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

  constructor(
    private workingTimesService: WorkingTimesService,
    private userAccountsService: UserAccountsService,
    private appointmentsService: AppointmentsService,
    private accountStateService: AccountStateService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService,
  ) {}

  ngOnInit(): void {
    this.loadAccounts();
    this.loadWorkingTimes();
  }

  loadAccounts(): void {
    this.userAccountsService.getMyAccounts().subscribe({
      next: (response) => {
        this.accounts.set(response.accounts || []);

        if (this.accounts().length > 0) {
          this.selectedAccountId = this.accounts()[0].accountId?.toString() || null;
          this.breakSelectedAccountId = this.accounts()[0].accountId?.toString() || null;
        }

        this.loadBreaks();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Accounts konnten nicht geladen werden.',
        });
      },
    });
  }

  loadWorkingTimes(): void {
    this.loading.set(true);

    this.workingTimesService.getWorkingTimes().subscribe({
      next: (response) => {
        this.workingTimes.set(response.items || []);
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Arbeitszeiten konnten nicht geladen werden.',
        });
        this.loading.set(false);
      },
    });
  }

  loadBreaks(): void {
    const accounts = this.accounts();

    if (accounts.length === 0) {
      this.breaks.set([]);
      return;
    }

    this.breaksLoading.set(true);

    const requests = accounts
      .filter((account) => !!account.accountId)
      .map((account) =>
        this.appointmentsService.listAppointments(account.accountId!.toString()).pipe(
          map((response: any) => {
            const items = response.items || response.appointments || response.content || [];

            return items.map((item: Appointment) => ({
              ...item,
              accountId: account.accountId,
            }));
          }),
          catchError(() => of([])),
        ),
      );

    forkJoin(requests).subscribe({
      next: (results) => {
        const allAppointments = results.flat();

        this.breaks.set(
          allAppointments.filter(
            (item: any) =>
              item.isBreak === true ||
              item.type === 'BREAK' ||
              item.appointmentType === 'BREAK' ||
              item.kind === 'BREAK' ||
              item.category === 'BREAK',
          ),
        );

        this.breaksLoading.set(false);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Pausen konnten nicht geladen werden.',
        });
        this.breaksLoading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    this.isEditMode.set(false);
    this.editingId = null;
    this.selectedDays = [];
    this.startTimeString = '08:00';
    this.endTimeString = '17:00';
    this.selectedAccountId =
      this.accounts().length > 0 ? this.accounts()[0].accountId?.toString() || null : null;
    this.showDialog.set(true);
  }

  openEditDialog(item: WorkingTimeResponse): void {
    this.isEditMode.set(true);
    this.editingId = item.id?.toString() || null;
    this.selectedAccountId = item.accountId?.toString() || null;
    this.selectedDays = item.days ? [...item.days] : [];
    this.startTimeString = item.startTime || '08:00';
    this.endTimeString = item.endTime || '17:00';
    this.showDialog.set(true);
  }

  saveWorkingTime(): void {
    if (!this.selectedAccountId) {
      this.showWarn('Bitte wählen Sie eine Organisation aus.');
      return;
    }

    if (!this.startTimeString) {
      this.showWarn('Bitte geben Sie eine Startzeit an.');
      return;
    }

    if (!this.endTimeString) {
      this.showWarn('Bitte geben Sie eine Endzeit an.');
      return;
    }

    if (this.selectedDays.length === 0) {
      this.showWarn('Bitte wählen Sie mindestens einen Wochentag aus.');
      return;
    }

    if (this.endTimeString <= this.startTimeString) {
      this.showWarn('Die Endzeit muss nach der Startzeit liegen.');
      return;
    }

    if (this.isEditMode()) {
      this.updateWorkingTime();
    } else {
      this.createWorkingTime();
    }
  }

  createWorkingTime(): void {
    const request: WorkingTimeCreateRequest = {
      accountId: this.selectedAccountId as any,
      days: this.selectedDays,
      startTime: this.startTimeString,
      endTime: this.endTimeString,
    };

    this.workingTimesService.createWorkingTime(request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Arbeitszeit erstellt.',
        });
        this.showDialog.set(false);
        this.loadWorkingTimes();
        this.accountStateService.refresh();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail:
            error.error?.detail ||
            error.error?.message ||
            'Arbeitszeit konnte nicht erstellt werden.',
        });
      },
    });
  }

  updateWorkingTime(): void {
    if (!this.editingId) return;

    const request: WorkingTimeUpdateRequest = {
      days: this.selectedDays,
      startTime: this.startTimeString,
      endTime: this.endTimeString,
    };

    this.workingTimesService.updateWorkingTime(this.editingId, request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Arbeitszeit aktualisiert.',
        });
        this.showDialog.set(false);
        this.loadWorkingTimes();
        this.accountStateService.refresh();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail:
            error.error?.detail ||
            error.error?.message ||
            'Arbeitszeit konnte nicht aktualisiert werden.',
        });
      },
    });
  }

  deleteWorkingTime(item: WorkingTimeResponse): void {
    this.confirmationService.confirm({
      message: 'Möchten Sie diese Arbeitszeit wirklich löschen?',
      header: 'Bestätigung',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Ja',
      rejectLabel: 'Nein',
      accept: () => {
        if (!item.id) return;

        this.workingTimesService.deleteWorkingTime(item.id.toString()).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Erfolg',
              detail: 'Arbeitszeit gelöscht.',
            });
            this.loadWorkingTimes();
            this.accountStateService.refresh();
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Fehler',
              detail: 'Arbeitszeit konnte nicht gelöscht werden.',
            });
          },
        });
      },
    });
  }

  openCreateBreakDialog(): void {
    this.isBreakEditMode.set(false);
    this.editingBreakId = null;
    this.editingBreakAccountId = null;

    this.breakTitle = '';
    this.breakDate = '';
    this.breakStartTime = '12:00';
    this.breakEndTime = '13:00';
    this.breakType = 'ONCE';
    this.breakSelectedDays = [];
    this.breakSelectedAccountId =
      this.accounts().length > 0 ? this.accounts()[0].accountId?.toString() || null : null;

    this.showBreakDialog.set(true);
  }

  openEditBreakDialog(item: Appointment): void {
    const appointmentId = (item as any).id?.toString();
    const accountId = (item as any).accountId?.toString();

    if (!appointmentId || !accountId) return;

    this.isBreakEditMode.set(true);
    this.editingBreakId = appointmentId;
    this.editingBreakAccountId = accountId;

    this.breakSelectedAccountId = accountId;
    this.breakTitle = this.getBreakTitle(item);
    this.breakStartTime = this.getBreakStart(item) || '12:00';
    this.breakEndTime = this.getBreakEnd(item) || '13:00';

    const appointmentType =
      (item as any).appointmentType || (item as any).type || (item as any).breakType || '';

    this.breakType =
      appointmentType === 'RECURRING' || appointmentType === 'WEEKDAYS' || !!(item as any).rrule
        ? 'RECURRING'
        : 'ONCE';
    const breakDate = this.getBreakDate(item);
    this.breakDate = this.breakType === 'ONCE' ? breakDate : '';
    this.breakSelectedDays =
      this.breakType === 'RECURRING' ? this.parseDaysFromRrule((item as any).rrule) : [];

    this.showBreakDialog.set(true);
  }

  saveBreak(): void {
    if (!this.breakSelectedAccountId) {
      this.showWarn('Bitte wählen Sie eine Organisation aus.');
      return;
    }

    if (!this.breakTitle.trim()) {
      this.showWarn('Bitte geben Sie eine Bezeichnung ein.');
      return;
    }

    if (this.breakType === 'ONCE' && !this.breakDate) {
      this.showWarn('Bitte geben Sie ein Datum ein.');
      return;
    }

    if (this.breakType === 'RECURRING' && this.breakSelectedDays.length === 0) {
      this.showWarn('Bitte wählen Sie mindestens einen Wochentag aus.');
      return;
    }

    if (!this.breakStartTime) {
      this.showWarn('Bitte geben Sie eine Startzeit ein.');
      return;
    }

    if (!this.breakEndTime) {
      this.showWarn('Bitte geben Sie eine Endzeit ein.');
      return;
    }

    if (this.breakEndTime <= this.breakStartTime) {
      this.showWarn('Die Endzeit muss nach der Startzeit liegen.');
      return;
    }

    const request: AppointmentCreate = {
      title: this.breakTitle.trim(),
      isBreak: true,
      appointmentType: this.breakType === 'ONCE' ? 'ONE_TIME' : 'RECURRING',
      date: this.breakType === 'ONCE' ? this.breakDate : null,
      startTime: this.breakStartTime,
      endTime: this.breakEndTime,
      rrule: this.breakType === 'RECURRING' ? this.buildWeeklyRrule(this.breakSelectedDays) : null,
    };

    if (this.isBreakEditMode()) {
      this.updateBreak(request);
    } else {
      this.createBreak(request);
    }
  }

  createBreak(request: AppointmentCreate): void {
    if (!this.breakSelectedAccountId) return;

    this.appointmentsService.createAppointment(this.breakSelectedAccountId, request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Pause wurde erstellt.',
        });
        this.showBreakDialog.set(false);
        this.loadBreaks();
        this.accountStateService.refresh();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail:
            error.error?.detail || error.error?.message || 'Pause konnte nicht erstellt werden.',
        });
      },
    });
  }

  updateBreak(request: AppointmentCreate): void {
    if (!this.editingBreakId || !this.editingBreakAccountId) return;

    this.appointmentsService
      .updateAppointment(this.editingBreakAccountId, this.editingBreakId, request as any)
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Erfolg',
            detail: 'Pause aktualisiert.',
          });
          this.showBreakDialog.set(false);
          this.loadBreaks();
          this.accountStateService.refresh();
        },
        error: (error) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Fehler',
            detail:
              error.error?.detail ||
              error.error?.message ||
              'Pause konnte nicht aktualisiert werden.',
          });
        },
      });
  }

  deleteBreak(item: Appointment): void {
    const appointmentId = (item as any).id?.toString();
    const accountId = (item as any).accountId?.toString();

    if (!appointmentId || !accountId) return;

    this.confirmationService.confirm({
      message: 'Möchten Sie diese Pause wirklich löschen?',
      header: 'Bestätigung',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Ja',
      rejectLabel: 'Nein',
      accept: () => {
        this.appointmentsService.deleteAppointment(accountId, appointmentId).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Erfolg',
              detail: 'Pause gelöscht.',
            });
            this.loadBreaks();
            this.accountStateService.refresh();
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Fehler',
              detail: 'Pause konnte nicht gelöscht werden.',
            });
          },
        });
      },
    });
  }

  getBreakTitle(item: Appointment): string {
    return (item as any).title || (item as any).name || (item as any).label || 'Pause';
  }

  getBreakStart(item: Appointment): string {
    return (
      (item as any).startTime ||
      this.extractTime((item as any).startAt) ||
      this.extractTime((item as any).startDateTime) ||
      ''
    );
  }

  getBreakEnd(item: Appointment): string {
    return (
      (item as any).endTime ||
      this.extractTime((item as any).endAt) ||
      this.extractTime((item as any).endDateTime) ||
      ''
    );
  }

  getBreakDate(item: Appointment): string {
    if ((item as any).appointmentType === 'RECURRING' || (item as any).rrule) {
      const days = this.parseDaysFromRrule((item as any).rrule);
      return days.length ? this.formatDays(days) : 'Wiederkehrend';
    }

    return (
      (item as any).date ||
      this.extractDate((item as any).startAt) ||
      this.extractDate((item as any).startDateTime) ||
      'Wiederkehrend'
    );
  }

  getBreakTypeLabel(item: Appointment): string {
    const breakType =
      (item as any).appointmentType ||
      (item as any).breakType ||
      (item as any).type ||
      (item as any).recurrence;

    if (breakType === 'ONE_TIME' || breakType === 'ONCE') return 'Einmalig';
    if (breakType === 'RECURRING' || breakType === 'WEEKDAYS') return 'Wiederkehrend';

    if ((item as any).rrule) return 'Wiederkehrend';

    return 'Pause';
  }

  private extractTime(value?: string): string {
    if (!value) return '';
    const match = value.match(/T(\d{2}:\d{2})/);
    return match ? match[1] : '';
  }

  private extractDate(value?: string): string {
    if (!value) return '';
    return value.split('T')[0] || '';
  }

  private showWarn(detail: string): void {
    this.messageService.add({
      severity: 'warn',
      summary: 'Validierung',
      detail,
    });
  }

  getAccountName(accountId: any): string {
    const account = this.accounts().find((a) => a.accountId?.toString() === accountId?.toString());
    return account ? account.organizationName || 'Unbekannt' : 'Unbekannt';
  }

  formatDays(days: DayOfWeek[] | undefined): string {
    if (!days || days.length === 0) return '';

    const labels: Record<string, string> = {
      MONDAY: 'Mo',
      TUESDAY: 'Di',
      WEDNESDAY: 'Mi',
      THURSDAY: 'Do',
      FRIDAY: 'Fr',
      SATURDAY: 'Sa',
      SUNDAY: 'So',
    };

    const order = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

    return days
      .slice()
      .sort((a, b) => order.indexOf(a.toUpperCase()) - order.indexOf(b.toUpperCase()))
      .map((d) => labels[d.toUpperCase()] ?? d)
      .join(', ');
  }

  private buildWeeklyRrule(days: DayOfWeek[]): string {
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

    return `FREQ=WEEKLY;BYDAY=${byDay}`;
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

  calculateDuration(startTime: string | undefined, endTime: string | undefined): string {
    if (!startTime || !endTime) return '0';

    const [sh, sm] = startTime.split(':').map(Number);
    const [eh, em] = endTime.split(':').map(Number);
    const totalMinutes = eh * 60 + em - (sh * 60 + sm);

    if (totalMinutes <= 0) return '0';

    const hours = Math.floor(totalMinutes / 60);
    const mins = totalMinutes % 60;

    return mins === 0 ? `${hours}h` : `${hours}h ${mins}min`;
  }
}
