import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  GenerateScheduleRequest,
  ScheduleEntry,
  ScheduleResponse,
  ScheduleWarning,
  SchedulesService,
  UnscheduledTask,
  UserAccountsService,
  WorkingTimesService,
} from '../../../api';

type AccountOption = {
  id: string;
  label: string;
};

export type ScheduleWorkingTime = {
  id: string | null;
  accountId: string | null;
  organizationId: string | null;
  days: string[];
  startTime: string;
  endTime: string;
  conflicting: boolean;
};

@Injectable({
  providedIn: 'root',
})
export class ScheduleFacadeService {
  private readonly schedulesService = inject(SchedulesService);
  private readonly userAccountsService = inject(UserAccountsService);
  private readonly workingTimesService = inject(WorkingTimesService);

  readonly accounts = signal<AccountOption[]>([]);
  readonly selectedAccountId = signal<string>('');

  readonly scheduleResponse = signal<ScheduleResponse | null>(null);
  readonly scheduleEntries = signal<ScheduleEntry[]>([]);
  readonly workingTimes = signal<ScheduleWorkingTime[]>([]);

  readonly isLoadingAccounts = signal(false);
  readonly isLoadingSchedule = signal(false);
  readonly isGenerating = signal(false);

  readonly errorMessage = signal('');
  readonly infoMessage = signal('');
  readonly successMessage = signal('');

  readonly lastRange = signal<{ from: string; to: string } | null>(null);

  private readonly canLoadExistingSchedule = signal(true);

  readonly hasAccounts = computed(() => this.accounts().length > 0);

  readonly hasSchedule = computed(
    () => this.scheduleEntries().length > 0 || this.workingTimes().length > 0,
  );

  readonly selectedAccount = computed(
    () => this.accounts().find((account) => account.id === this.selectedAccountId()) ?? null,
  );

  readonly warnings = computed<ScheduleWarning[]>(() => this.scheduleResponse()?.warnings ?? []);

  readonly unscheduledTasks = computed<UnscheduledTask[]>(
    () => this.scheduleResponse()?.unscheduledTasks ?? [],
  );

  readonly hasPartialSchedule = computed(
    () => this.unscheduledTasks().length > 0 || this.warnings().length > 0,
  );

  async initialize(): Promise<void> {
    this.selectedAccountId.set('');
    await this.loadAccounts();

    if (this.selectedAccountId()) {
      const range = this.getCurrentWeekRange();
      await this.loadWorkingTimes(this.selectedAccountId());
      await this.loadSchedule(range.from, range.to);
    }
  }

  async loadAccounts(): Promise<void> {
    this.isLoadingAccounts.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    try {
      const response = await firstValueFrom(this.userAccountsService.getMyAccounts());
      const accounts = this.normalizeAccountResponse(response.accounts);

      this.accounts.set(accounts);
    } catch (error) {
      this.accounts.set([]);
      this.errorMessage.set(
        this.getReadableErrorMessage(
          error,
          'Accounts konnten nicht geladen werden. Bitte versuche es später erneut.',
        ),
      );
    } finally {
      this.isLoadingAccounts.set(false);
    }
  }

  async selectAccount(accountId: string): Promise<void> {
    this.selectedAccountId.set(accountId);
    this.scheduleResponse.set(null);
    this.scheduleEntries.set([]);
    this.workingTimes.set([]);
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.successMessage.set('');

    if (!accountId) {
      return;
    }

    const range = this.lastRange() ?? this.getCurrentWeekRange();

    await this.loadWorkingTimes(accountId);
    await this.loadSchedule(range.from, range.to);
  }

  async loadSchedule(from: string, to: string): Promise<void> {
    this.lastRange.set({ from, to });

    const accountId = this.selectedAccountId();

    if (!accountId || !this.canLoadExistingSchedule()) {
      return;
    }

    this.isLoadingSchedule.set(true);
    this.errorMessage.set('');

    try {
      const response = await firstValueFrom(
        this.schedulesService.getSingleAccountSchedule(accountId, from, to),
      );

      this.applyScheduleResponse(response);
      this.infoMessage.set('');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 501) {
        this.canLoadExistingSchedule.set(false);

        if (!this.hasSchedule()) {
          this.infoMessage.set(
            'Vorhandene Planungen können aktuell noch nicht geladen werden, da der Backend-Endpunkt noch nicht implementiert ist. Du kannst aber eine neue Planung starten.',
          );
        }

        return;
      }

      this.scheduleResponse.set(null);
      this.scheduleEntries.set([]);

      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Der Arbeitsplan konnte nicht geladen werden.'),
      );
    } finally {
      this.isLoadingSchedule.set(false);
    }
  }

  async generateSchedule(): Promise<void> {
    const accountId = this.selectedAccountId();

    if (!accountId) {
      this.errorMessage.set('Bitte wähle zuerst ein Unternehmen oder einen Verein aus.');
      return;
    }

    this.isGenerating.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.successMessage.set('');

    const request: GenerateScheduleRequest = {
      from: this.getGenerationStartDate(),
    };

    try {
      await this.loadWorkingTimes(accountId);

      const response = await firstValueFrom(
        this.schedulesService.generateSingleAccountSchedule(accountId, request),
      );

      this.applyScheduleResponse(response);

      if (
        (response.entries?.length ?? 0) > 0 ||
        ((response as any).appointments?.length ?? 0) > 0
      ) {
        this.successMessage.set('Die Planung wurde erfolgreich erstellt.');
      } else {
        this.successMessage.set('');
        this.infoMessage.set(
          'Die Planung wurde verarbeitet, aber es wurden keine Einträge erzeugt.',
        );
      }
    } catch (error) {
      this.successMessage.set('');
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Die Planung konnte nicht gestartet werden.'),
      );
    } finally {
      this.isGenerating.set(false);
    }
  }

  getUnscheduledTaskLabel(task: UnscheduledTask): string {
    const value = task as any;

    return (
      value.title ||
      value.taskTitle ||
      value.name ||
      value.taskId ||
      value.originalItemId ||
      'Nicht eingeplante Aufgabe'
    );
  }

  private async loadWorkingTimes(accountId: string): Promise<void> {
    try {
      const response = await firstValueFrom(this.workingTimesService.getWorkingTimes());
      const workingTimes = this.normalizeWorkingTimeResponse(response, accountId);

      this.workingTimes.set(workingTimes);
    } catch (error) {
      this.workingTimes.set([]);

      if (!this.errorMessage()) {
        this.errorMessage.set(
          this.getReadableErrorMessage(
            error,
            'Arbeitszeiten konnten nicht geladen werden. Nicht-Arbeitszeiten können deshalb nicht korrekt markiert werden.',
          ),
        );
      }
    }
  }

  private applyScheduleResponse(response: ScheduleResponse): void {
    const scheduleEntries = response.entries ?? [];
    const appointmentEntries = this.mapAppointmentsToScheduleEntries(response);

    const mergedEntries = this.sortScheduleEntries(
      this.deduplicateScheduleEntries([...scheduleEntries, ...appointmentEntries]),
    );

    this.scheduleResponse.set(response);
    this.scheduleEntries.set(mergedEntries);
  }

  private mapAppointmentsToScheduleEntries(response: ScheduleResponse): ScheduleEntry[] {
    const appointments = (response as any).appointments ?? [];

    return appointments
      .filter((appointment: any) => this.isValidAppointmentForCalendar(appointment))
      .map((appointment: any): ScheduleEntry => {
        return {
          source: appointment.appointmentType ?? 'ONE_TIME',
          startTime: appointment.startTime,
          endTime: appointment.endTime,
          type: 'APPOINTMENT',
          isCompleted: false,
          isPinned: false,
          originalItemTitle: appointment.title ?? 'Termin',
          chunkIndex: null,
          entryId: appointment.id ?? null,
          isBreak: appointment.isBreak === true,
          occurrenceDate: appointment.date,
          originalItemId: appointment.id ?? null,
          totalChunks: null,
        } as ScheduleEntry;
      });
  }

  private isValidAppointmentForCalendar(appointment: any): boolean {
    return Boolean(appointment && appointment.date && appointment.startTime && appointment.endTime);
  }

  private deduplicateScheduleEntries(entries: ScheduleEntry[]): ScheduleEntry[] {
    const seen = new Set<string>();

    return entries.filter((entry) => {
      const key = [
        entry.entryId ?? '',
        entry.originalItemId ?? '',
        entry.occurrenceDate ?? '',
        entry.startTime ?? '',
        entry.endTime ?? '',
        entry.type ?? '',
        entry.isBreak === true ? 'break' : 'normal',
      ].join('|');

      if (seen.has(key)) {
        return false;
      }

      seen.add(key);
      return true;
    });
  }

  private sortScheduleEntries(entries: ScheduleEntry[]): ScheduleEntry[] {
    return [...entries].sort((a, b) => {
      const dateCompare = String(a.occurrenceDate ?? '').localeCompare(
        String(b.occurrenceDate ?? ''),
      );

      if (dateCompare !== 0) {
        return dateCompare;
      }

      return String(a.startTime ?? '').localeCompare(String(b.startTime ?? ''));
    });
  }

  private normalizeWorkingTimeResponse(response: any, accountId: string): ScheduleWorkingTime[] {
    const items = Array.isArray(response) ? response : (response?.items ?? []);

    return items
      .filter((item: any) => {
        if (!item) {
          return false;
        }

        if (item.accountId && String(item.accountId) !== accountId) {
          return false;
        }

        return Boolean(item.days?.length && item.startTime && item.endTime);
      })
      .map((item: any): ScheduleWorkingTime => {
        return {
          id: item.id ?? null,
          accountId: item.accountId ?? null,
          organizationId: item.organizationId ?? null,
          days: item.days ?? [],
          startTime: item.startTime,
          endTime: item.endTime,
          conflicting: item.conflicting === true,
        };
      });
  }

  private getGenerationStartDate(): string {
    const range = this.lastRange() ?? this.getCurrentWeekRange();
    const today = this.toIsoDate(new Date());

    return range.from < today ? today : range.from;
  }

  private getCurrentWeekRange(): { from: string; to: string } {
    const today = new Date();
    const day = today.getDay();
    const diffToMonday = day === 0 ? -6 : 1 - day;

    const monday = new Date(today);
    monday.setDate(today.getDate() + diffToMonday);

    const nextMonday = new Date(monday);
    nextMonday.setDate(monday.getDate() + 7);

    return {
      from: this.toIsoDate(monday),
      to: this.toIsoDate(nextMonday),
    };
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  }

  private normalizeAccountResponse(accounts: any[] | undefined): AccountOption[] {
    if (!accounts) {
      return [];
    }

    return accounts
      .map((account: any) => {
        const id = account.accountId ?? account.id;
        const label =
          account.organizationName ??
          account.label ??
          account.name ??
          account.displayName ??
          'Account';

        if (!id) {
          return null;
        }

        return {
          id: String(id),
          label: String(label),
        };
      })
      .filter((item): item is AccountOption => item !== null);
  }

  private getReadableErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (typeof error.error === 'string' && error.error.trim()) {
      return error.error;
    }

    if (error.error?.errors?.length) {
      return error.error.errors
        .map((item: { message?: string }) => item.message)
        .filter(Boolean)
        .join(' ');
    }

    return error.error?.message || error.error?.detail || error.error?.error || fallback;
  }
}
