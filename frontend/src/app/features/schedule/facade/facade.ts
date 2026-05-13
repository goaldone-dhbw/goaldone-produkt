import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  GenerateScheduleRequest,
  MultiAccountScheduleResponse,
  ScheduleEntry,
  ScheduleResponse,
  ScheduleWarning,
  SchedulesService,
  UnscheduledTask,
  UserAccountsService,
} from '../../../api';

type AccountOption = {
  id: string;
  label: string;
};

@Injectable({
  providedIn: 'root',
})
export class ScheduleFacadeService {
  private readonly schedulesService = inject(SchedulesService);
  private readonly userAccountsService = inject(UserAccountsService);
  private readonly ALL_ACCOUNTS_ID = 'ALL';
  private readonly STORAGE_KEY = 'lastScheduleEntries';

  readonly accounts = signal<AccountOption[]>([]);
  readonly selectedAccountId = signal<string>(this.ALL_ACCOUNTS_ID);

  readonly scheduleResponse = signal<ScheduleResponse | null>(null);
  readonly scheduleEntries = signal<ScheduleEntry[]>([]);

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
  readonly hasSchedule = computed(() => this.scheduleEntries().length > 0);

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
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.successMessage.set('');
    this.canLoadExistingSchedule.set(true);
    this.skipNextLoad.set(false);

    if (!accountId) {
      return;
    }

    const range = this.lastRange() ?? this.getCurrentWeekRange();
    await this.loadSchedule(range.from, range.to);
  }

  async loadSchedule(from: string, to: string): Promise<void> {
    this.lastRange.set({ from, to });

    // Wenn gerade generiert wurde, diesen Load überspringen
    if (this.skipNextLoad()) {
      this.skipNextLoad.set(false);
      return;
    }

    const accountId = this.selectedAccountId();

    if (!accountId) return;
    if (accountId !== this.ALL_ACCOUNTS_ID && !this.canLoadExistingSchedule()) return;

    // Ladeindikator nur zeigen wenn noch nichts angezeigt wird
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

      this.applyScheduleResponse(response);
      this.infoMessage.set('');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 501) {
        if (accountId !== this.ALL_ACCOUNTS_ID) {
          this.canLoadExistingSchedule.set(false);
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

      // Einträge nicht löschen wenn wir bereits etwas anzeigen (z.B. aus Cache)
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
      const response =
        accountId === this.ALL_ACCOUNTS_ID
          ? await firstValueFrom(this.schedulesService.generateAllAccountsSchedule(request))
          : await firstValueFrom(
            this.schedulesService.generateSingleAccountSchedule(accountId, request),
          );

      this.applyScheduleResponse(response);

      // Verhindert dass rangeChanged-Event danach die Daten überschreibt
      this.skipNextLoad.set(true);

      if (this.scheduleEntries().length > 0) {
        this.successMessage.set('Die Planung wurde erfolgreich erstellt.');
      } else {
        this.infoMessage.set(
          'Die Planung wurde verarbeitet, aber es wurden keine Einträge erzeugt.',
        );
      }
    } catch (error) {
      console.error('Fehler bei generateSchedule:', error);
      if (error instanceof HttpErrorResponse) {
        console.error('Status:', error.status, 'Body:', error.error);
      }
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

  private applyScheduleResponse(response: ScheduleResponse | MultiAccountScheduleResponse): void {
    if ('schedules' in response) {
      const mergedEntries = response.schedules.flatMap((schedule) => schedule.entries ?? []);
      const mergedWarnings = response.schedules.flatMap((schedule) => schedule.warnings ?? []);
      const mergedUnscheduledTasks = response.schedules.flatMap(
        (schedule) => schedule.unscheduledTasks ?? [],
      );

      const firstSchedule = response.schedules[0];
      const currentRange = this.lastRange() ?? this.getCurrentWeekRange();

      const mergedResponse: ScheduleResponse = {
        accountId: 'ALL',
        generatedAt: firstSchedule?.generatedAt ?? new Date().toISOString(),
        from: firstSchedule?.from ?? currentRange.from,
        to: firstSchedule?.to ?? currentRange.to,
        totalWorkMinutes: response.schedules.reduce(
          (sum, schedule) => sum + (schedule.totalWorkMinutes ?? 0),
          0,
        ),
        score: response.schedules.reduce(
          (sum, schedule) => sum + (schedule.score ?? 0),
          0,
        ),
        warnings: mergedWarnings,
        entries: mergedEntries,
        unscheduledTasks: mergedUnscheduledTasks,
      };

      this.scheduleResponse.set(mergedResponse);
      this.scheduleEntries.set(mergedEntries);
      this.saveToCache(mergedEntries);
      return;
    }

    const entries = response.entries ?? [];
    this.scheduleResponse.set(response);
    this.scheduleEntries.set(entries);
    this.saveToCache(entries);
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
