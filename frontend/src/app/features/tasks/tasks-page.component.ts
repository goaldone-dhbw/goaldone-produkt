import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { Tooltip } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { FormsModule } from '@angular/forms';
import {
  CognitiveLoad,
  TaskResponse,
  TasksService,
  TaskStatus,
  TaskUpdateRequest,
  UserAccountsService,
} from '../../api';
import { BasePopupComponent } from '../../shared/base-popup/base-popup.component';
import {
  TaskEditDialogComponent,
  TaskItem,
} from '../../shared/task-edit-dialog/task-edit-dialog.component';

type AccountOption = {
  id: string;
  label: string;
};

type TaskFilters = {
  status: TaskStatus | null;
  difficulty: CognitiveLoad | null;
  deadlineFrom: Date | null;
  deadlineTo: Date | null;
  accountId: string | null;
  searchTerm: string | null;
  maxDuration: number | null;
};

type HideableSelect = {
  hide(isFocus?: boolean): void;
};

@Component({
  selector: 'app-tasks-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    SelectModule,
    DatePickerModule,
    InputTextModule,
    InputNumberModule,
    Tooltip,
    TaskEditDialogComponent,
    BasePopupComponent,
  ],
  templateUrl: './tasks-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksPageComponent implements OnInit {
  private static readonly INVALID_DATE_RANGE_MESSAGE =
    'Das Startdatum muss vor dem Enddatum liegen';

  private readonly tasksService = inject(TasksService);
  private readonly userAccountsService = inject(UserAccountsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly tasks = signal<TaskItem[]>([]);
  readonly totalTaskCount = signal(0);
  readonly accounts = signal<AccountOption[]>([]);

  readonly isLoading = signal(false);
  readonly isDeleting = signal(false);

  readonly listErrorMessage = signal('');
  readonly successMessage = signal('');

  isTaskPopupOpen = signal(false);
  isDeletePopupOpen = signal(false);

  editingTask = signal<TaskItem | null>(null);
  deletingTask = signal<TaskItem | null>(null);

  readonly currentAccount = computed<AccountOption | null>(() => {
    const accounts = this.accounts();
    return accounts.length === 1 ? accounts[0] : null;
  });

  ngOnInit(): void {
    void this.initializePage();
  }

  private async initializePage(): Promise<void> {
    await this.loadAccounts();
    this.route.queryParams.subscribe((params) => {
      this.parseQueryParams(params);
      void this.loadTasks();
    });
  }

  private parseQueryParams(params: any): void {
    this.filters = {
      status: params.status || null,
      difficulty: params.difficulty || null,
      accountId: params.accountId || null,
      searchTerm: params.searchTerm || null,
      maxDuration: params.maxDuration ? parseInt(params.maxDuration, 10) : null,
      deadlineFrom: params.deadlineFrom
        ? this.parseLocalDateNativeString(params.deadlineFrom)
        : null,
      deadlineTo: params.deadlineTo ? this.parseLocalDateNativeString(params.deadlineTo) : null,
    };
    this.dateRange = [];
    if (this.filters.deadlineFrom) {
      this.dateRange[0] = this.filters.deadlineFrom;
    }
    if (this.filters.deadlineTo && this.filters.deadlineFrom) {
      this.dateRange[1] = this.filters.deadlineTo;
    } else if (this.filters.deadlineTo && !this.filters.deadlineFrom) {
      this.dateRange = [this.filters.deadlineTo];
    }
  }

  private parseLocalDateNativeString(val: string): Date | null {
    if (!val) return null;
    const d = new Date(val);
    return isNaN(d.getTime()) ? null : d;
  }

  private formatLocalDateNativeString(date: Date): string {
    const pad = (num: number) => String(num).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  async loadAccounts(): Promise<void> {
    try {
      const response = await firstValueFrom(this.userAccountsService.getMyAccounts());
      const normalized = this.normalizeAccountResponse(response.accounts);
      this.accounts.set(normalized);
    } catch {
      this.accounts.set([]);
    }
  }

  async loadTasks(): Promise<void> {
    if (!this.validateFilters()) {
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);

    try {
      const fromStr = this.filters.deadlineFrom
        ? this.formatLocalDateNativeString(this.filters.deadlineFrom)
        : undefined;
      const toStr = this.filters.deadlineTo
        ? this.formatLocalDateNativeString(this.filters.deadlineTo)
        : undefined;

      const response = await firstValueFrom(
        this.tasksService.getTasks(
          this.filters.status || undefined,
          this.filters.difficulty || undefined,
          fromStr,
          toStr,
          undefined,
          this.filters.maxDuration || undefined,
          undefined,
          undefined,
          this.filters.searchTerm || undefined,
        ),
      );

      const allTasks: TaskItem[] = response
        .map((t) => this.mapTaskResponse(t, t.accountId ?? ''))
        .filter((task) => {
          return !(this.filters.accountId && task.accountId !== this.filters.accountId);
        });

      this.listErrorMessage.set('');
      this.successMessage.set('');
      this.totalTaskCount.set(allTasks.length);
      this.tasks.set(allTasks);
    } catch (error) {
      this.tasks.set([]);
      this.totalTaskCount.set(0);
      this.listErrorMessage.set(
        this.getReadableErrorMessage(error, 'Aufgaben konnten nicht geladen werden.'),
      );
    } finally {
      this.isLoading.set(false);
    }
  }

  private validateFilters(): boolean {
    this.listErrorMessage.set('');
    this.successMessage.set('');

    if (
      this.filters.deadlineFrom &&
      this.filters.deadlineTo &&
      this.filters.deadlineFrom > this.filters.deadlineTo
    ) {
      this.listErrorMessage.set(TasksPageComponent.INVALID_DATE_RANGE_MESSAGE);
      return false;
    }

    return true;
  }

  getEmptyTasksMessage(): string {
    if (this.hasActiveFilters()) {
      return 'Zu diesem Filter sind keine Aufgaben vorhanden.';
    }

    return 'Es sind noch keine Aufgaben vorhanden.';
  }

  private hasActiveFilters(): boolean {
    return (
      this.filters.status !== null ||
      this.filters.difficulty !== null ||
      this.filters.deadlineFrom !== null ||
      this.filters.deadlineTo !== null ||
      this.filters.accountId !== null ||
      this.filters.searchTerm !== null ||
      this.filters.maxDuration !== null
    );
  }

  applyFilterStateToUrl(): void {
    if (!this.validateFilters()) {
      return;
    }

    const queryParams: any = {};
    if (this.filters.status) queryParams.status = this.filters.status;
    if (this.filters.difficulty) queryParams.difficulty = this.filters.difficulty;
    if (this.filters.accountId) queryParams.accountId = this.filters.accountId;
    if (this.filters.searchTerm) queryParams.searchTerm = this.filters.searchTerm;
    if (this.filters.maxDuration) queryParams.maxDuration = this.filters.maxDuration;
    if (this.filters.deadlineFrom)
      queryParams.deadlineFrom = this.formatLocalDateNativeString(this.filters.deadlineFrom);
    if (this.filters.deadlineTo)
      queryParams.deadlineTo = this.formatLocalDateNativeString(this.filters.deadlineTo);

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: '',
    });
  }

  openCreateDialog(): void {
    this.successMessage.set('');
    this.editingTask.set(null);
    this.isTaskPopupOpen.set(true);
  }

  openEditDialog(task: TaskItem): void {
    this.successMessage.set('');
    this.editingTask.set(task);
    this.isTaskPopupOpen.set(true);
  }

  onTaskSaved(): void {
    this.successMessage.set('Die Aufgabe wurde erfolgreich gespeichert.');
    void this.loadTasks();
  }

  async changeStatus(task: TaskItem, event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const nextStatus = select.value as TaskStatus;

    const payload: TaskUpdateRequest = {
      title: task.title,
      description: task.description || undefined,
      duration: task.duration,
      deadline: task.deadline || undefined,
      status: nextStatus,
      cognitiveLoad: task.cognitiveLoad || undefined,
      dontScheduleBefore: task.dontScheduleBefore || undefined,
      customChunkSize: task.customChunkSize || undefined,
      dependencyIds: task.dependencyIds || [],
    };

    try {
      await firstValueFrom(this.tasksService.updateTask(task.id, payload));
      this.successMessage.set('Der Status wurde gespeichert.');
      await this.loadTasks();
    } catch (error) {
      this.listErrorMessage.set(
        this.getReadableErrorMessage(error, 'Der Status konnte nicht gespeichert werden.'),
      );
      await this.loadTasks();
    }
  }

  openDeleteDialog(task: TaskItem): void {
    this.deletingTask.set(task);
    this.isDeletePopupOpen.set(true);
  }

  closeDeletePopup(): void {
    this.isDeletePopupOpen.set(false);
    this.deletingTask.set(null);
  }

  async confirmDelete(): Promise<void> {
    const task = this.deletingTask();

    if (!task) {
      return;
    }

    this.isDeleting.set(true);
    this.listErrorMessage.set('');
    this.successMessage.set('');

    try {
      await firstValueFrom(this.tasksService.deleteTask(task.id));
      this.successMessage.set('Die Aufgabe wurde erfolgreich gelöscht.');
      this.isDeletePopupOpen.set(false);
      this.deletingTask.set(null);
      await this.loadTasks();
    } catch (error) {
      this.listErrorMessage.set(
        this.getReadableErrorMessage(error, 'Die Aufgabe konnte nicht gelöscht werden.'),
      );
    } finally {
      this.isDeleting.set(false);
    }
  }

  formatStatus(status: TaskStatus): string {
    switch (status) {
      case 'OPEN':
        return 'Offen';
      case 'IN_PROGRESS':
        return 'In Bearbeitung';
      case 'DONE':
        return 'Erledigt';
      default:
        return status;
    }
  }

  formatCognitiveLoad(load: CognitiveLoad): string {
    switch (load) {
      case 'LOW':
        return 'Niedrig';
      case 'MODERATE':
        return 'Mittel';
      case 'HIGH':
        return 'Hoch';
      default:
        return load;
    }
  }

  getStatusBadgeClass(status: TaskStatus): string {
    switch (status) {
      case 'OPEN':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      case 'IN_PROGRESS':
        return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'DONE':
        return 'bg-emerald-100 text-emerald-800 border-emerald-200';
      default:
        return 'bg-slate-100 text-slate-800 border-slate-200';
    }
  }

  getCognitiveLoadBadgeClass(load: CognitiveLoad): string {
    switch (load) {
      case 'LOW':
        return 'bg-emerald-100 text-emerald-800 border-emerald-200';
      case 'MODERATE':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      case 'HIGH':
        return 'bg-rose-100 text-rose-800 border-rose-200';
      default:
        return 'bg-slate-100 text-slate-800 border-slate-200';
    }
  }

  formatDuration(minutes: number | null): string {
    if (!minutes || minutes <= 0) {
      return '-';
    }

    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;

    if (hours > 0 && restMinutes > 0) {
      return `${hours} h ${restMinutes} min`;
    }

    if (hours > 0) {
      return `${hours} h`;
    }

    return `${restMinutes} min`;
  }

  formatDateTime(value: string | null): string {
    if (!value) {
      return '-';
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  }

  formatDependencyTitles(task: TaskItem): string {
    if (!task.dependencyIds?.length) {
      return '-';
    }

    const titleMap = new Map(this.tasks().map((item) => [item.id, item.title]));
    const titles = task.dependencyIds
      .map((id) => titleMap.get(id))
      .filter((title): title is string => !!title);

    return titles.length ? titles.join(', ') : `${task.dependencyIds.length} ausgewählt`;
  }

  private mapTaskResponse(task: TaskResponse, accountId: string): TaskItem {
    return {
      id: task.id ?? '',
      title: task.title ?? '',
      description: task.description ?? null,
      duration: task.duration ?? 0,
      deadline: task.deadline ?? null,
      status: task.status ?? 'OPEN',
      accountId,
      accountLabel: this.resolveAccountLabel(accountId),
      dependencyIds: task.dependencyIds ?? [],
      cognitiveLoad: task.cognitiveLoad ?? null,
      dontScheduleBefore: task.dontScheduleBefore ?? null,
      customChunkSize: task.customChunkSize ?? null,
    };
  }

  private normalizeAccountResponse(accounts: any[] | undefined): AccountOption[] {
    if (!accounts) return [];

    return accounts
      .map((account: any) => {
        const id = account.accountId ?? account.id;
        const label =
          account.organizationName ?? account.label ?? account.name ?? account.displayName;

        if (!id || !label) return null;

        return { id: String(id), label: String(label) };
      })
      .filter((item): item is AccountOption => item !== null);
  }

  private resolveAccountLabel(accountId: string | null): string | null {
    if (!accountId) {
      return null;
    }

    return this.accounts().find((account) => account.id === accountId)?.label ?? null;
  }

  private getReadableErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (typeof error.error === 'string' && error.error.trim()) {
      return error.error;
    }

    return error.error?.message || error.error?.detail || error.error?.error || fallback;
  }

  filters: TaskFilters = {
    status: null,
    difficulty: null,
    deadlineFrom: null,
    deadlineTo: null,
    accountId: null,
    searchTerm: null,
    maxDuration: null,
  };

  dateRange: Date[] = [];

  statusOptions = [
    { label: 'Offen', value: 'OPEN' },
    { label: 'In Bearbeitung', value: 'IN_PROGRESS' },
    { label: 'Erledigt', value: 'DONE' },
  ];

  difficultyOptions = [
    { label: 'Hoch', value: 'HIGH' },
    { label: 'Mittel', value: 'MODERATE' },
    { label: 'Niedrig', value: 'LOW' },
  ];

  onDateRangeChange(): void {
    this.filters.deadlineFrom = this.dateRange?.[0] || null;
    this.filters.deadlineTo = this.dateRange?.[1] || null;
  }

  onDateRangeSelect(selectedDate: Date): void {
    const previousRangeStart = this.filters.deadlineFrom;
    const previousRangeEnd = this.filters.deadlineTo;

    if (
      previousRangeStart &&
      previousRangeEnd &&
      this.isSameOrBetweenDates(selectedDate, previousRangeStart, previousRangeEnd)
    ) {
      this.clearDateRangeFilter();
      return;
    }

    this.onDateRangeChange();
  }

  clearDateRangeFilter(): void {
    this.dateRange = [];
    this.filters.deadlineFrom = null;
    this.filters.deadlineTo = null;
  }

  private isSameOrBetweenDates(date: Date, start: Date, end: Date): boolean {
    const day = this.getDateOnlyTime(date);
    const startDay = this.getDateOnlyTime(start);
    const endDay = this.getDateOnlyTime(end);

    return day >= startDay && day <= endDay;
  }

  private getDateOnlyTime(date: Date): number {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  }

  toggleSelectFilter(
    event: Event,
    select: HideableSelect,
    filterKey: 'status',
    value: TaskStatus,
  ): void;
  toggleSelectFilter(
    event: Event,
    select: HideableSelect,
    filterKey: 'difficulty',
    value: CognitiveLoad,
  ): void;
  toggleSelectFilter(
    event: Event,
    select: HideableSelect,
    filterKey: 'accountId',
    value: string,
  ): void;
  toggleSelectFilter(
    event: Event,
    select: HideableSelect,
    filterKey: keyof Pick<TaskFilters, 'status' | 'difficulty' | 'accountId'>,
    value: TaskStatus | CognitiveLoad | string,
  ): void {
    event.stopPropagation();

    if (filterKey === 'status') {
      const status = value as TaskStatus;
      this.filters.status = this.filters.status === status ? null : status;
    } else if (filterKey === 'difficulty') {
      const difficulty = value as CognitiveLoad;
      this.filters.difficulty = this.filters.difficulty === difficulty ? null : difficulty;
    } else {
      this.filters.accountId = this.filters.accountId === value ? null : value;
    }

    select.hide(true);
  }

  resetFilters(): void {
    this.filters = {
      status: null,
      difficulty: null,
      deadlineFrom: null,
      deadlineTo: null,
      accountId: null,
      searchTerm: null,
      maxDuration: null,
    };

    this.dateRange = [];
    this.listErrorMessage.set('');
    this.successMessage.set('');
    this.applyFilterStateToUrl();
  }

  private openCreateDialogFromQueryParam(): void {
    const shouldOpenCreateDialog = this.route.snapshot.queryParamMap.get('create') === 'true';

    if (!shouldOpenCreateDialog) {
      return;
    }

    this.openCreateDialog();

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { create: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
