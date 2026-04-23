import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { BasePopupComponent } from '../../../shared/base-popup/base-popup.component';

type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE';
type CognitiveLoad = 'LOW' | 'MODERATE' | 'HIGH';

type AccountOption = {
  id: string;
  label: string;
};

type DependencyOption = {
  id: string;
  title: string;
};

type FrontendOnlyTaskMeta = {
  cognitiveLoad: CognitiveLoad | null;
  notBefore: string | null;
  customChunkSize: number | null;
};

type TaskItem = {
  id: string;
  title: string;
  description: string | null;
  duration: number;
  deadline: string | null;
  status: TaskStatus;
  accountId: string | null;
  accountLabel: string | null;
  dependencyIds: string[];
  cognitiveLoad: CognitiveLoad | null;
  notBefore: string | null;
  customChunkSize: number | null;
};

type TaskFormValue = {
  id: string | null;
  title: string;
  description: string;
  duration: number | null;
  deadline: string;
  status: TaskStatus;
  accountId: string;
  dependencyIds: string[];
  cognitiveLoad: CognitiveLoad | '';
  notBefore: string;
  customChunkSize: number | null;
};

type TaskApiResponse = {
  id?: string;
  title?: string;
  description?: string | null;
  duration?: number;
  deadline?: string | null;
  status?: TaskStatus;
  accountId?: string;
  account?: {
    accountId?: string;
    id?: string;
    organizationName?: string;
  };
  accountLabel?: string;
  organizationName?: string;
  companyName?: string;
  clubName?: string;
  dependencyIds?: unknown[];
  cognitiveLoad?: CognitiveLoad | null;
  notBefore?: string | null;
  customChunkSize?: number | null;
};

type AccountsApiResponse = {
  accounts?: Array<{
    accountId?: string;
    id?: string;
    organizationName?: string;
    label?: string;
    name?: string;
    displayName?: string;
  }>;
};

@Component({
  selector: 'app-tasks-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, BasePopupComponent],
  templateUrl: './tasks-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);

  private readonly apiBasePath = this.readApiBasePath();
  private readonly tasksUrl = `${this.apiBasePath}/tasks`;
  private readonly accountsUrl = `${this.apiBasePath}/users/accounts`;

  private readonly localMetaStorageKey = 'goaldone.tasks.frontend-meta';

  readonly tasks = signal<TaskItem[]>([]);
  readonly accounts = signal<AccountOption[]>([]);

  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isDeleting = signal(false);

  readonly listErrorMessage = signal('');
  readonly formErrorMessage = signal('');
  readonly successMessage = signal('');

  readonly isTaskPopupOpen = signal(false);
  readonly isDeletePopupOpen = signal(false);

  readonly editingTaskId = signal<string | null>(null);
  readonly deletingTask = signal<TaskItem | null>(null);

  readonly statuses: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE'];
  readonly cognitiveLoads: CognitiveLoad[] = ['LOW', 'MODERATE', 'HIGH'];

  readonly hasMultipleAccounts = computed(() => this.accounts().length > 1);
  readonly hasSingleAccount = computed(() => this.accounts().length === 1);

  readonly currentAccount = computed<AccountOption | null>(() => {
    const accounts = this.accounts();
    return accounts.length === 1 ? accounts[0] : null;
  });

  readonly currentAccountLabel = computed(() => this.currentAccount()?.label ?? null);

  readonly popupTitle = computed(() =>
    this.editingTaskId() ? 'Aufgabe bearbeiten' : 'Aufgabe erstellen',
  );

  readonly popupConfirmLabel = computed(() =>
    this.editingTaskId() ? 'Änderungen speichern' : 'Aufgabe speichern',
  );

  readonly taskForm = this.fb.group(
    {
      id: this.fb.control<string | null>(null),
      title: this.fb.control('', [Validators.required, Validators.maxLength(150)]),
      description: this.fb.control(''),
      duration: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
      deadline: this.fb.control(''),
      status: this.fb.control<TaskStatus>('OPEN', [Validators.required]),
      accountId: this.fb.control('', [Validators.required]),
      dependencyIds: this.fb.control<string[]>([]),
      cognitiveLoad: this.fb.control<CognitiveLoad | ''>(''),
      notBefore: this.fb.control(''),
      customChunkSize: this.fb.control<number | null>(null),
    },
    {
      validators: [this.dateRelationValidator.bind(this), this.chunkSizeValidator.bind(this)],
    },
  );

  readonly availableDependencyOptions = computed<DependencyOption[]>(() => {
    const currentId = this.editingTaskId();

    return this.tasks()
      .filter((task) => task.id !== currentId)
      .map((task) => ({
        id: task.id,
        title: this.buildDependencyOptionLabel(task),
      }));
  });

  constructor() {
    void this.initializePage();
  }

  private async initializePage(): Promise<void> {
    await this.loadAccounts();
    await this.loadTasks();
  }

  async loadAccounts(): Promise<void> {
    try {
      const response = await firstValueFrom(this.http.get<unknown>(this.accountsUrl));
      const normalized = this.normalizeAccountResponse(response);
      this.accounts.set(normalized);

      if (normalized.length === 1) {
        this.taskForm.patchValue({ accountId: normalized[0].id });
      }
    } catch {
      this.accounts.set([]);
    }
  }

  async loadTasks(): Promise<void> {
    this.isLoading.set(true);
    this.listErrorMessage.set('');

    try {
      const response = await firstValueFrom(this.http.get<unknown[]>(this.tasksUrl));
      const mapped = (response ?? []).map((item: unknown) => this.mapTaskResponse(item));
      this.tasks.set(mapped);
    } catch (error) {
      this.tasks.set([]);
      this.listErrorMessage.set(
        this.getReadableErrorMessage(
          error,
          'Aufgaben konnten nicht geladen werden. Das Backend ist möglicherweise noch nicht verfügbar.',
        ),
      );
    } finally {
      this.isLoading.set(false);
    }
  }

  openCreateDialog(): void {
    this.successMessage.set('');
    this.formErrorMessage.set('');
    this.editingTaskId.set(null);

    this.taskForm.reset({
      id: null,
      title: '',
      description: '',
      duration: null,
      deadline: '',
      status: 'OPEN',
      accountId: this.getDefaultAccountId(),
      dependencyIds: [],
      cognitiveLoad: '',
      notBefore: '',
      customChunkSize: null,
    });

    this.isTaskPopupOpen.set(true);
  }

  openEditDialog(task: TaskItem): void {
    this.successMessage.set('');
    this.formErrorMessage.set('');
    this.editingTaskId.set(task.id);

    this.taskForm.reset({
      id: task.id,
      title: task.title,
      description: task.description ?? '',
      duration: task.duration,
      deadline: this.toDateTimeLocalValue(task.deadline),
      status: task.status,
      accountId: task.accountId ?? this.getDefaultAccountId(),
      dependencyIds: task.dependencyIds ?? [],
      cognitiveLoad: task.cognitiveLoad ?? '',
      notBefore: this.toDateTimeLocalValue(task.notBefore),
      customChunkSize: task.customChunkSize,
    });

    this.isTaskPopupOpen.set(true);
  }

  closeTaskPopup(): void {
    this.isTaskPopupOpen.set(false);
    this.formErrorMessage.set('');
  }

  async saveTask(): Promise<void> {
    this.successMessage.set('');
    this.formErrorMessage.set('');
    this.taskForm.markAllAsTouched();

    if (this.taskForm.invalid) {
      this.formErrorMessage.set('Bitte fülle alle Pflichtfelder korrekt aus.');
      return;
    }

    const payload = this.buildTaskPayload();
    const frontendOnlyMeta = this.buildFrontendOnlyMeta();

    if (!payload || !frontendOnlyMeta) {
      this.formErrorMessage.set('Die Aufgabe konnte nicht verarbeitet werden.');
      return;
    }

    this.isSaving.set(true);

    try {
      if (this.editingTaskId()) {
        const taskId = this.editingTaskId() as string;
        await firstValueFrom(this.http.put(`${this.tasksUrl}/${taskId}`, payload));
        this.saveLocalMeta(taskId, frontendOnlyMeta);
        this.successMessage.set('Die Aufgabe wurde erfolgreich aktualisiert.');
      } else {
        const createdTask = await firstValueFrom(this.http.post<unknown>(this.tasksUrl, payload));
        const createdId = this.extractTaskId(createdTask);

        if (createdId) {
          this.saveLocalMeta(createdId, frontendOnlyMeta);
        }

        this.successMessage.set('Die Aufgabe wurde erfolgreich erstellt.');
      }

      this.isTaskPopupOpen.set(false);
      await this.loadTasks();
    } catch (error) {
      this.formErrorMessage.set(
        this.getReadableErrorMessage(error, 'Die Aufgabe konnte nicht gespeichert werden.'),
      );
    } finally {
      this.isSaving.set(false);
    }
  }

  async changeStatus(task: TaskItem, event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const nextStatus = select.value as TaskStatus;

    const payload: Record<string, unknown> = {
      title: task.title,
      description: task.description,
      duration: task.duration,
      deadline: task.deadline,
      status: nextStatus,
      dependencyIds: task.dependencyIds,
    };

    if (task.accountId) {
      payload['accountId'] = task.accountId;
    }

    try {
      await firstValueFrom(this.http.put(`${this.tasksUrl}/${task.id}`, payload));
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
      await firstValueFrom(this.http.delete(`${this.tasksUrl}/${task.id}`));
      this.removeLocalMeta(task.id);
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

  showFieldError(fieldName: 'title' | 'duration' | 'accountId' | 'customChunkSize'): boolean {
    const control = this.taskForm.get(fieldName);
    const hasFormLevelChunkError =
      fieldName === 'customChunkSize' && !!this.taskForm.errors?.['chunkSizeInvalid'];

    return (
      (!!control && control.invalid && (control.touched || control.dirty)) || hasFormLevelChunkError
    );
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

  private getDefaultAccountId(): string {
    return this.currentAccount()?.id ?? '';
  }

  private buildDependencyOptionLabel(task: TaskItem): string {
    const parts: string[] = [task.title];

    if (task.status) {
      parts.push(this.formatStatus(task.status));
    }

    if (task.deadline) {
      parts.push(`Deadline: ${this.formatDateTime(task.deadline)}`);
    }

    return parts.join(' • ');
  }

  private buildTaskPayload(): Record<string, unknown> | null {
    const value = this.taskForm.getRawValue() as TaskFormValue;

    if (!value.title || !value.duration || !value.status || !value.accountId) {
      return null;
    }

    return {
      title: value.title.trim(),
      description: value.description?.trim() ? value.description.trim() : null,
      duration: Number(value.duration),
      deadline: value.deadline ? new Date(value.deadline).toISOString() : null,
      status: value.status,
      accountId: value.accountId,
      dependencyIds: value.dependencyIds ?? [],
    };
  }

  private buildFrontendOnlyMeta(): FrontendOnlyTaskMeta | null {
    const value = this.taskForm.getRawValue() as TaskFormValue;

    return {
      cognitiveLoad: value.cognitiveLoad || null,
      notBefore: value.notBefore ? new Date(value.notBefore).toISOString() : null,
      customChunkSize: value.customChunkSize ? Number(value.customChunkSize) : null,
    };
  }

  private extractTaskId(response: unknown): string | null {
    const data = response as { id?: unknown };
    return data?.id ? String(data.id) : null;
  }

  private mapTaskResponse(item: unknown): TaskItem {
    const task = item as TaskApiResponse;

    const dependencyIds = Array.isArray(task.dependencyIds)
      ? task.dependencyIds.map((id: unknown) => String(id))
      : [];

    const accountId =
      task.accountId != null
        ? String(task.accountId)
        : task.account?.accountId != null
          ? String(task.account.accountId)
          : task.account?.id != null
            ? String(task.account.id)
            : (this.currentAccount()?.id ?? null);

    const localMeta = task.id ? this.getLocalMeta(String(task.id)) : null;

    return {
      id: String(task.id ?? ''),
      title: task.title ?? '',
      description: task.description ?? null,
      duration: Number(task.duration ?? 0),
      deadline: task.deadline ?? null,
      status: (task.status ?? 'OPEN') as TaskStatus,
      accountId,
      accountLabel:
        this.resolveAccountLabel(accountId) ??
        task.accountLabel ??
        task.organizationName ??
        task.account?.organizationName ??
        task.companyName ??
        task.clubName ??
        (this.accounts().length === 1 ? (this.currentAccount()?.label ?? null) : null),
      dependencyIds,
      cognitiveLoad: task.cognitiveLoad ?? localMeta?.cognitiveLoad ?? null,
      notBefore: task.notBefore ?? localMeta?.notBefore ?? null,
      customChunkSize:
        task.customChunkSize != null
          ? Number(task.customChunkSize)
          : (localMeta?.customChunkSize ?? null),
    };
  }

  private normalizeAccountResponse(response: unknown): AccountOption[] {
    const root = response as AccountsApiResponse;
    const rawItems: unknown[] = Array.isArray(root?.accounts) ? root.accounts : [];

    return rawItems
      .map((item: unknown) => {
        const account = item as {
          accountId?: string;
          id?: string;
          organizationName?: string;
          label?: string;
          name?: string;
          displayName?: string;
        };

        const id = account.accountId ?? account.id ?? null;
        const label =
          account.organizationName ?? account.label ?? account.name ?? account.displayName ?? null;

        if (!id || !label) {
          return null;
        }

        return {
          id: String(id),
          label: String(label),
        } as AccountOption | null;
      })
      .filter((item: AccountOption | null): item is AccountOption => item !== null);
  }

  private resolveAccountLabel(accountId: string | null): string | null {
    if (!accountId) {
      return null;
    }

    return this.accounts().find((account) => account.id === accountId)?.label ?? null;
  }

  private toDateTimeLocalValue(value: string | null): string {
    if (!value) {
      return '';
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return '';
    }

    const pad = (input: number) => String(input).padStart(2, '0');

    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    const hour = pad(date.getHours());
    const minute = pad(date.getMinutes());

    return `${year}-${month}-${day}T${hour}:${minute}`;
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

  private dateRelationValidator(control: AbstractControl): ValidationErrors | null {
    const deadline = control.get('deadline')?.value;
    const notBefore = control.get('notBefore')?.value;

    if (!deadline || !notBefore) {
      return null;
    }

    const deadlineDate = new Date(deadline).getTime();
    const notBeforeDate = new Date(notBefore).getTime();

    if (notBeforeDate > deadlineDate) {
      return { notBeforeAfterDeadline: true };
    }

    return null;
  }

  private chunkSizeValidator(control: AbstractControl): ValidationErrors | null {
    const duration = Number(control.get('duration')?.value);
    const customChunkSize = Number(control.get('customChunkSize')?.value);

    if (!customChunkSize) {
      return null;
    }

    if (customChunkSize <= 0 || (duration > 0 && customChunkSize > duration)) {
      return { chunkSizeInvalid: true };
    }

    return null;
  }

  private getLocalMeta(taskId: string): FrontendOnlyTaskMeta | null {
    const allMeta = this.readAllLocalMeta();
    return allMeta[taskId] ?? null;
  }

  private saveLocalMeta(taskId: string, meta: FrontendOnlyTaskMeta): void {
    const allMeta = this.readAllLocalMeta();
    allMeta[taskId] = meta;
    this.writeAllLocalMeta(allMeta);
  }

  private removeLocalMeta(taskId: string): void {
    const allMeta = this.readAllLocalMeta();
    delete allMeta[taskId];
    this.writeAllLocalMeta(allMeta);
  }

  private readAllLocalMeta(): Record<string, FrontendOnlyTaskMeta> {
    try {
      const raw = localStorage.getItem(this.localMetaStorageKey);

      if (!raw) {
        return {};
      }

      const parsed = JSON.parse(raw) as Record<string, FrontendOnlyTaskMeta>;
      return parsed ?? {};
    } catch {
      return {};
    }
  }

  private writeAllLocalMeta(meta: Record<string, FrontendOnlyTaskMeta>): void {
    try {
      localStorage.setItem(this.localMetaStorageKey, JSON.stringify(meta));
    } catch {
    }
  }

  private readApiBasePath(): string {
    const rawValue = (window as any).__env?.['apiBasePath'] || 'http://localhost:8080';
    return String(rawValue).replace(/\/+$/, '');
  }
}
