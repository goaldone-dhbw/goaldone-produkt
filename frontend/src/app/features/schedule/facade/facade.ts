import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  AppointmentsService,
  GenerateScheduleRequest,
  MultiAccountScheduleResponse,
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

type CalendarDayName =
  | 'SUNDAY'
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY';

const DAY_TOKEN_MAP: Record<string, CalendarDayName> = {
  SUNDAY: 'SUNDAY',
  SUN: 'SUNDAY',
  SU: 'SUNDAY',
  SO: 'SUNDAY',
  SONNTAG: 'SUNDAY',

  MONDAY: 'MONDAY',
  MON: 'MONDAY',
  MO: 'MONDAY',
  MONTAG: 'MONDAY',

  TUESDAY: 'TUESDAY',
  TUE: 'TUESDAY',
  TU: 'TUESDAY',
  DI: 'TUESDAY',
  DIENSTAG: 'TUESDAY',

  WEDNESDAY: 'WEDNESDAY',
  WED: 'WEDNESDAY',
  WE: 'WEDNESDAY',
  MI: 'WEDNESDAY',
  MITTWOCH: 'WEDNESDAY',

  THURSDAY: 'THURSDAY',
  THU: 'THURSDAY',
  TH: 'THURSDAY',
  DO: 'THURSDAY',
  DONNERSTAG: 'THURSDAY',

  FRIDAY: 'FRIDAY',
  FRI: 'FRIDAY',
  FR: 'FRIDAY',
  FREITAG: 'FRIDAY',

  SATURDAY: 'SATURDAY',
  SAT: 'SATURDAY',
  SA: 'SATURDAY',
  SAMSTAG: 'SATURDAY',
};

@Injectable({
  providedIn: 'root',
})
export class ScheduleFacadeService {
  private readonly schedulesService = inject(SchedulesService);
  private readonly userAccountsService = inject(UserAccountsService);
  private readonly workingTimesService = inject(WorkingTimesService);
  private readonly appointmentsService = inject(AppointmentsService);

  private readonly ALL_ACCOUNTS_ID = 'ALL';
  private readonly STORAGE_KEY = 'lastScheduleEntries';

  readonly accounts = signal<AccountOption[]>([]);
  readonly selectedAccountId = signal<string>(this.ALL_ACCOUNTS_ID);

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
  private readonly skipNextLoad = signal(false);

  readonly hasAccounts = computed(() => this.accounts().length > 0);

  readonly hasSchedule = computed(
    () => this.scheduleEntries().length > 0 || this.workingTimes().length > 0,
  );

  readonly warnings = computed<ScheduleWarning[]>(() => this.scheduleResponse()?.warnings ?? []);

  readonly unscheduledTasks = computed<UnscheduledTask[]>(
    () => this.scheduleResponse()?.unscheduledTasks ?? [],
  );

  readonly hasPartialSchedule = computed(
    () => this.unscheduledTasks().length > 0 || this.warnings().length > 0,
  );

  async initialize(): Promise<void> {
    this.selectedAccountId.set(this.ALL_ACCOUNTS_ID);
    this.canLoadExistingSchedule.set(true);
    this.skipNextLoad.set(false);

    this.restoreFromCache();

    await this.loadAccounts();

    if (this.hasAccounts()) {
      const range = this.getCurrentWeekRange();
      await this.loadWorkingTimes(this.selectedAccountId());
      await this.loadSchedule(range.from, range.to);
    }
  }

  private restoreFromCache(): void {
    try {
      const cached = localStorage.getItem(this.STORAGE_KEY);
      if (cached) {
        const entries: ScheduleEntry[] = JSON.parse(cached);
        this.scheduleEntries.set(entries);
      }
    } catch {
      // Cache kaputt, ignorieren
    }
  }

  private saveToCache(entries: ScheduleEntry[]): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(entries));
    } catch {
      // Speicher voll, kein Caching
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
    this.canLoadExistingSchedule.set(true);
    this.skipNextLoad.set(false);

    if (!accountId) {
      return;
    }

    const range = this.lastRange() ?? this.getCurrentWeekRange();

    await this.loadWorkingTimes(accountId);
    await this.loadSchedule(range.from, range.to);
  }

  async loadSchedule(from: string, to: string): Promise<void> {
    this.lastRange.set({ from, to });

    if (this.skipNextLoad()) {
      this.skipNextLoad.set(false);

      const accountId = this.selectedAccountId();
      const existingResponse = this.scheduleResponse();

      if (accountId) {
        const appointmentEntries = await this.loadAppointmentEntries(accountId);

        if (existingResponse) {
          this.applyScheduleResponse(
            this.removeAppointmentEntriesFromResponse(existingResponse),
            appointmentEntries,
          );
        } else if (appointmentEntries.length > 0) {
          const entries = this.sortScheduleEntries(appointmentEntries);
          this.scheduleEntries.set(entries);
          this.saveToCache(entries);
        }
      }

      return;
    }

    const accountId = this.selectedAccountId();
    const existingResponse = this.scheduleResponse();

    if (!accountId) {
      return;
    }

    if (!this.canLoadExistingSchedule()) {
      if (existingResponse) {
        const appointmentEntries = await this.loadAppointmentEntries(accountId);

        this.applyScheduleResponse(
          this.removeAppointmentEntriesFromResponse(existingResponse),
          appointmentEntries,
        );
      }
    }


    if (accountId !== this.ALL_ACCOUNTS_ID && !this.canLoadExistingSchedule()) return;

    if (!this.hasSchedule()) {
      this.isLoadingSchedule.set(true);
    }

    this.errorMessage.set('');

    try {
      const response =
        accountId === this.ALL_ACCOUNTS_ID
          ? await firstValueFrom(this.schedulesService.getAllAccountsSchedule(from, to))
          : await firstValueFrom(
              this.schedulesService.getSingleAccountSchedule(accountId, from, to),
            );

      const appointmentEntries = await this.loadAppointmentEntries(accountId);

      this.applyScheduleResponse(response, appointmentEntries);
      this.infoMessage.set('');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 501) {
        if (accountId !== this.ALL_ACCOUNTS_ID) {
          this.canLoadExistingSchedule.set(false);
        }

        const fallbackResponse = this.scheduleResponse();
        const appointmentEntries = await this.loadAppointmentEntries(accountId);

        if (fallbackResponse) {
          this.applyScheduleResponse(
            this.removeAppointmentEntriesFromResponse(fallbackResponse),
            appointmentEntries,
          );
        } else if (appointmentEntries.length > 0) {
          this.scheduleEntries.set(appointmentEntries);
          this.saveToCache(appointmentEntries);
        }

        if (!this.hasSchedule()) {
          this.infoMessage.set(
            accountId === this.ALL_ACCOUNTS_ID
              ? ''
              : 'Vorhandene Planungen können aktuell noch nicht geladen werden. Du kannst aber eine neue Planung starten.',
          );
        }

        return;
      }

      if (!this.hasSchedule()) {
        this.scheduleResponse.set(null);
        this.scheduleEntries.set([]);
      }

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

      const response =
        accountId === this.ALL_ACCOUNTS_ID
          ? await firstValueFrom(this.schedulesService.generateAllAccountsSchedule(request))
          : await firstValueFrom(
              this.schedulesService.generateSingleAccountSchedule(accountId, request),
            );

      const appointmentEntries = await this.loadAppointmentEntries(accountId);

      this.applyScheduleResponse(response, appointmentEntries);

      this.skipNextLoad.set(true);

      if (
        ((response as any).entries?.length ?? 0) > 0 ||
        ((response as any).appointments?.length ?? 0) > 0 ||
        appointmentEntries.length > 0 ||
        (response as any).schedules?.some(
          (schedule: any) =>
            (schedule.appointments?.length ?? 0) > 0 || (schedule.entries?.length ?? 0) > 0,
        )
      ) {
        this.successMessage.set('Die Planung wurde erfolgreich erstellt.');
      } else {
        this.infoMessage.set(
          'Die Planung wurde verarbeitet, aber es wurden keine Einträge erzeugt.',
        );
      }
    } catch (error) {
      this.errorMessage.set(
        this.getReadableErrorMessage(error, 'Die Planung konnte nicht gestartet werden.'),
      );
    } finally {
      this.isGenerating.set(false);
    }
  }

  async refreshAppointments(): Promise<void> {
    const accountId = this.selectedAccountId();

    if (!accountId) {
      return;
    }

    const appointmentEntries = await this.loadAppointmentEntries(accountId);

    const freshBreakEntries = appointmentEntries.filter(
      (entry) => entry.type === 'APPOINTMENT' && entry.isBreak === true,
    );

    const currentResponse = this.scheduleResponse();

    if (currentResponse) {
      const responseWithoutOldBreaks: ScheduleResponse = {
        ...currentResponse,
        entries: (currentResponse.entries ?? []).filter(
          (entry) => !(entry.type === 'APPOINTMENT' && entry.isBreak === true),
        ),
        appointments: [],
      };

      this.applyScheduleResponse(responseWithoutOldBreaks, freshBreakEntries);
      return;
    }

    const existingNonBreakEntries = this.scheduleEntries().filter(
      (entry) => !(entry.type === 'APPOINTMENT' && entry.isBreak === true),
    );

    const entries = this.sortScheduleEntries([
      ...existingNonBreakEntries,
      ...freshBreakEntries,
    ]);

    this.scheduleEntries.set(entries);
    this.saveToCache(entries);
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
  private removeAppointmentEntriesFromResponse(response: ScheduleResponse): ScheduleResponse {
    return {
      ...response,
      entries: (response.entries ?? []).filter(
        (entry) => entry.type !== 'APPOINTMENT',
      ),
      appointments: [],
    };
  }

  private applyScheduleResponse(
    response: ScheduleResponse | MultiAccountScheduleResponse,
    externalAppointmentEntries: ScheduleEntry[] = [],
  ): void {
    if (Array.isArray((response as MultiAccountScheduleResponse).schedules)) {
      const multiResponse = response as MultiAccountScheduleResponse;

      const rawEntries = multiResponse.schedules.flatMap((schedule) => schedule.entries ?? []);

      const responseAppointments = multiResponse.schedules.flatMap(
        (schedule) => schedule.appointments ?? [],
      );

      const responseAppointmentEntries =
        this.mapAppointmentsToScheduleEntries(responseAppointments);

      const mergedEntries = this.sortScheduleEntries(
        this.deduplicateScheduleEntries([
          ...rawEntries,
          ...responseAppointmentEntries,
          ...externalAppointmentEntries,
        ]),
      );

      const mergedWarnings = multiResponse.schedules.flatMap((schedule) => schedule.warnings ?? []);
      const mergedUnscheduledTasks = multiResponse.schedules.flatMap(
        (schedule) => schedule.unscheduledTasks ?? [],
      );

      const firstSchedule = multiResponse.schedules[0];
      const currentRange = this.lastRange() ?? this.getCurrentWeekRange();

      const mergedResponse: ScheduleResponse = {
        accountId: 'ALL',
        generatedAt: firstSchedule?.generatedAt ?? new Date().toISOString(),
        from: firstSchedule?.from ?? currentRange.from,
        to: firstSchedule?.to ?? currentRange.to,
        totalWorkMinutes: multiResponse.schedules.reduce(
          (sum, schedule) => sum + (schedule.totalWorkMinutes ?? 0),
          0,
        ),
        score: multiResponse.schedules.reduce((sum, schedule) => sum + (schedule.score ?? 0), 0),
        warnings: mergedWarnings,
        entries: mergedEntries,
        unscheduledTasks: mergedUnscheduledTasks,
        appointments: responseAppointments,
      };

      this.scheduleResponse.set(mergedResponse);
      this.scheduleEntries.set(mergedEntries);
      this.saveToCache(mergedEntries);
      return;
    }

    const singleResponse = response as ScheduleResponse;

    const rawEntries = singleResponse.entries ?? [];
    const responseAppointmentEntries = this.mapAppointmentsToScheduleEntries(
      (singleResponse as any).appointments ?? [],
    );

    const entries = this.sortScheduleEntries(
      this.deduplicateScheduleEntries([
        ...rawEntries,
        ...responseAppointmentEntries,
        ...externalAppointmentEntries,
      ]),
    );

    const normalizedResponse: ScheduleResponse = {
      ...singleResponse,
      entries,
    };

    this.scheduleResponse.set(normalizedResponse);
    this.scheduleEntries.set(entries);
    this.saveToCache(entries);
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

  private async loadAppointmentEntries(accountId: string): Promise<ScheduleEntry[]> {
    const appointments = await this.loadAppointmentsForSelection(accountId);
    return this.mapAppointmentsToScheduleEntries(appointments);
  }

  private async loadAppointmentsForSelection(accountId: string): Promise<any[]> {
    const accountIds =
      accountId === this.ALL_ACCOUNTS_ID
        ? this.accounts()
            .map((account) => account.id)
            .filter(Boolean)
        : [accountId];

    if (accountIds.length === 0) {
      return [];
    }

    const results = await Promise.all(
      accountIds.map(async (currentAccountId) => {
        try {
          const response = await firstValueFrom(
            this.appointmentsService.listAppointments(currentAccountId),
          );

          return this.extractAppointmentItems(response).map((appointment) => {
            const resolvedAccountId = appointment.accountId ?? currentAccountId;
            const accountLabel = this.getAccountLabel(resolvedAccountId);

            return {
              ...appointment,
              accountId: String(resolvedAccountId),
              accountLabel: accountLabel ?? null,
            };
          });
        } catch {
          return [];
        }
      }),
    );

    return results.flat();
  }

  private extractAppointmentItems(response: any): any[] {
    if (Array.isArray(response)) {
      return response;
    }

    if (Array.isArray(response?.appointments)) {
      return response.appointments;
    }

    if (Array.isArray(response?.items)) {
      return response.items;
    }

    if (Array.isArray(response?.data)) {
      return response.data;
    }

    return [];
  }

  private mapAppointmentsToScheduleEntries(appointments: any[]): ScheduleEntry[] {
    if (!Array.isArray(appointments)) {
      return [];
    }

    return appointments.flatMap((appointment) => {
      if (this.isRecurringAppointment(appointment)) {
        return this.expandRecurringAppointment(appointment);
      }

      if (this.isValidAppointmentForCalendar(appointment)) {
        return [this.mapSingleAppointment(appointment)];
      }

      return [];
    });
  }

  private isRecurringAppointment(appointment: any): boolean {
    return Boolean(
      appointment &&
      this.getAppointmentRrule(appointment) &&
      appointment.startTime &&
      appointment.endTime,
    );
  }

  private mapSingleAppointment(appointment: any): ScheduleEntry {
    const accountId = appointment.accountId ? String(appointment.accountId) : null;
    const accountLabel =
      appointment.accountLabel ??
      this.getAccountLabel(accountId);

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
      occurrenceDate: this.getAppointmentDate(appointment),
      originalItemId: appointment.id ?? null,
      totalChunks: null,
      ...(accountId ? { accountId } : {}),
      ...(accountLabel ? { accountLabel: String(accountLabel) } : {}),
    } as ScheduleEntry;
  }

  private expandRecurringAppointment(appointment: any): ScheduleEntry[] {
    const range = this.lastRange() ?? this.getCurrentWeekRange();
    const rrule = this.getAppointmentRrule(appointment);

    if (!rrule) {
      return [];
    }

    const recurringDays = this.getRecurringDays(appointment, rrule);
    const isDaily = this.isDailyRrule(rrule);
    const untilDate = this.parseUntilDateFromRrule(rrule);
    const startDate = this.getAppointmentDate(appointment);

    const accountId = appointment.accountId ? String(appointment.accountId) : null;
    const accountLabel =
      appointment.accountLabel ??
      this.getAccountLabel(accountId);

    if (recurringDays.length === 0 && !isDaily) {
      return [];
    }

    const dates = this.getDatesInRange(range.from, range.to);

    const dayNameMap: Record<number, CalendarDayName> = {
      0: 'SUNDAY',
      1: 'MONDAY',
      2: 'TUESDAY',
      3: 'WEDNESDAY',
      4: 'THURSDAY',
      5: 'FRIDAY',
      6: 'SATURDAY',
    };

    return dates
      .filter((date) => {
        if (startDate && date < startDate) {
          return false;
        }

        if (untilDate && date > untilDate) {
          return false;
        }

        if (isDaily) {
          return true;
        }

        const [year, month, day] = date.split('-').map(Number);
        const jsDay = new Date(year, month - 1, day).getDay();

        return recurringDays.includes(dayNameMap[jsDay]);
      })
      .map(
        (date): ScheduleEntry =>
          ({
            source: 'RECURRING',
            startTime: appointment.startTime,
            endTime: appointment.endTime,
            type: 'APPOINTMENT',
            isCompleted: false,
            isPinned: false,
            originalItemTitle: appointment.title ?? 'Termin',
            chunkIndex: null,
            entryId: `${appointment.id ?? 'recurring'}-${date}`,
            isBreak: appointment.isBreak === true,
            occurrenceDate: date,
            originalItemId: appointment.id ?? null,
            totalChunks: null,
            ...(accountId ? { accountId } : {}),
            ...(accountLabel ? { accountLabel: String(accountLabel) } : {}),
            ...(rrule ? { rrule } : {}),
            ...(startDate ? { originalStartDate: startDate } : {}),
          }) as ScheduleEntry,
      );
  }

  private getAppointmentRrule(appointment: any): string | null {
    const value =
      appointment?.rrule ??
      appointment?.rule ??
      appointment?.recurrenceRule ??
      appointment?.recurrence;

    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }

    return value.trim();
  }

  private getRecurringDays(appointment: any, rrule: string): CalendarDayName[] {
    const daysFromRule = this.parseDaysFromRrule(rrule);

    if (daysFromRule.length > 0) {
      return daysFromRule;
    }

    const rawDays =
      appointment?.days ??
      appointment?.weekDays ??
      appointment?.recurrenceDays ??
      appointment?.dayOfWeek ??
      [];

    if (Array.isArray(rawDays)) {
      return rawDays
        .flatMap((day) => this.normalizeDayValue(String(day)))
        .filter((day): day is CalendarDayName => Boolean(day));
    }

    if (typeof rawDays === 'string') {
      return rawDays
        .split(',')
        .map((day) => this.normalizeDayValue(day))
        .filter((day): day is CalendarDayName => Boolean(day));
    }

    return [];
  }

  private normalizeDayValue(value: string): CalendarDayName | null {
    const normalized = value
      .trim()
      .toUpperCase()
      .replace(/\./g, '')
      .replace(/\s+/g, '')
      .replace(/Ä/g, 'AE')
      .replace(/Ö/g, 'OE')
      .replace(/Ü/g, 'UE');

    return DAY_TOKEN_MAP[normalized] ?? null;
  }

  private getAppointmentDate(appointment: any): string {
    const value =
      appointment?.date ??
      appointment?.occurrenceDate ??
      appointment?.startDate ??
      appointment?.start;

    if (typeof value !== 'string' || value.trim() === '') {
      return '';
    }

    return this.normalizeDateValue(value);
  }

  private normalizeDateValue(value: string): string {
    const trimmed = value.trim();
    const firstTen = trimmed.substring(0, 10);

    if (/^\d{4}-\d{2}-\d{2}$/.test(firstTen)) {
      return firstTen;
    }

    const compact = trimmed.substring(0, 8);

    if (/^\d{8}$/.test(compact)) {
      return `${compact.substring(0, 4)}-${compact.substring(4, 6)}-${compact.substring(6, 8)}`;
    }

    return '';
  }

  private parseDaysFromRrule(rrule: string): CalendarDayName[] {
    const byDay = rrule
      .toUpperCase()
      .split(';')
      .find((part) => part.startsWith('BYDAY='))
      ?.substring('BYDAY='.length);

    if (!byDay) return [];

    return byDay
      .split(',')
      .map((code) => code.trim().slice(-2))
      .map((code) => this.normalizeDayValue(code))
      .filter((day): day is CalendarDayName => Boolean(day));
  }

  private isDailyRrule(rrule: string): boolean {
    return rrule
      .toUpperCase()
      .split(';')
      .some((part) => part === 'FREQ=DAILY' || part === 'RRULE:FREQ=DAILY');
  }

  private getDatesInRange(from: string, to: string): string[] {
    const dates: string[] = [];
    const [fy, fm, fd] = from.split('-').map(Number);
    const [ty, tm, td] = to.split('-').map(Number);
    const current = new Date(fy, fm - 1, fd);
    const end = new Date(ty, tm - 1, td);

    while (current < end) {
      const y = current.getFullYear();
      const m = String(current.getMonth() + 1).padStart(2, '0');
      const d = String(current.getDate()).padStart(2, '0');
      dates.push(`${y}-${m}-${d}`);
      current.setDate(current.getDate() + 1);
    }

    return dates;
  }

  private isValidAppointmentForCalendar(appointment: any): boolean {
    return Boolean(
      appointment &&
      this.getAppointmentDate(appointment) &&
      appointment.startTime &&
      appointment.endTime,
    );
  }

  private deduplicateScheduleEntries(entries: ScheduleEntry[]): ScheduleEntry[] {
    const seen = new Set<string>();

    return entries.filter((entry) => {
      const rawEntry = entry as any;

      const sourceId =
        entry.originalItemId ??
        rawEntry.appointmentId ??
        rawEntry.taskId ??
        rawEntry.originalTaskId ??
        entry.entryId ??
        '';

      const key = [
        sourceId,
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

        if (
          accountId !== this.ALL_ACCOUNTS_ID &&
          item.accountId &&
          String(item.accountId) !== accountId
        ) {
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

    const now = new Date();
    const today = this.toIsoDate(now);

    let date: Date;

    if (range.from < today) {
      date = now;
    } else {
      date = new Date(`${range.from}T00:00:00Z`);
    }

    return date.toISOString();
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

  private getAccountLabel(accountId: string | null | undefined): string | null {
    if (!accountId) {
      return null;
    }

    return this.accounts().find((account) => account.id === String(accountId))?.label ?? null;
  }
}
