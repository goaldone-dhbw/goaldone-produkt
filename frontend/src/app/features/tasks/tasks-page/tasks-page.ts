import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import {
  CognitiveLoad,
  TaskCreateRequest,
  TaskResponse,
  TaskStatus,
  TaskUpdateRequest,
} from '../../../api';
import { TasksService } from '../../../api';
import { UserAccountsService } from '../../../api';
import { BasePopupComponent } from '../../../shared/base-popup/base-popup.component';

type AccountOption = {
  id: string;
  label: string;
};

type DependencyOption = {
  id: string;
  title: string;
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
  dontScheduleBefore: string | null;
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
  dontScheduleBefore: string;
  customChunkSize: number | null;
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
  private readonly tasksService = inject(TasksService);
  private readonly userAccountsService = inject(UserAccountsService);

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
      dontScheduleBefore: this.fb.control(''),
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
      const response = await firstValueFrom(this.userAccountsService.getMyAccounts());
      const normalized = this.normalizeAccountResponse(response.accounts);
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
      const response = await firstValueFrom(this.tasksService.getTasksForAllAccounts());
      const allTasks: TaskItem[] = [];

      for (const accountTasks of response) {
        if (accountTasks.tasks) {
          const mapped = accountTasks.tasks.map((t) => this.mapTaskResponse(t, accountTasks.accountId));
          allTasks.push(...mapped);
        }
      }

      this.tasks.set(allTasks);
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
      dontScheduleBefore: '',
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
      dontScheduleBefore: this.toDateTimeLocalValue(task.dontScheduleBefore),
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

    const value = this.taskForm.getRawValue() as TaskFormValue;
    this.isSaving.set(true);

    try {
      if (this.editingTaskId()) {
        const taskId = this.editingTaskId() as string;
        const payload = this.buildUpdatePayload(value);
        await firstValueFrom(this.tasksService.updateTask(taskId, payload));
        this.successMessage.set('Die Aufgabe wurde erfolgreich aktualisiert.');
      } else {
        const payload = this.buildCreatePayload(value);
        await firstValueFrom(this.tasksService.createTask(payload));
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

    const payload: TaskUpdateRequest = {
      status: nextStatus,
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

  private buildCreatePayload(value: TaskFormValue): TaskCreateRequest {
    return {
      accountId: value.accountId,
      title: value.title.trim(),
      description: value.description?.trim() || undefined,
      duration: Number(value.duration),
      deadline: value.deadline ? new Date(value.deadline).toISOString() : undefined,
      status: value.status,
      cognitiveLoad: (value.cognitiveLoad || 'MODERATE') as CognitiveLoad,
      dontScheduleBefore: value.dontScheduleBefore ? new Date(value.dontScheduleBefore).toISOString() : undefined,
      customChunkSize: value.customChunkSize ? Number(value.customChunkSize) : undefined,
      dependencyIds: value.dependencyIds ?? [],
    };
  }

  private buildUpdatePayload(value: TaskFormValue): TaskUpdateRequest {
    return {
      title: value.title.trim(),
      description: value.description?.trim() || undefined,
      duration: Number(value.duration),
      deadline: value.deadline ? new Date(value.deadline).toISOString() : undefined,
      status: value.status,
      cognitiveLoad: (value.cognitiveLoad || 'MODERATE') as CognitiveLoad,
      dontScheduleBefore: value.dontScheduleBefore ? new Date(value.dontScheduleBefore).toISOString() : undefined,
      customChunkSize: value.customChunkSize ? Number(value.customChunkSize) : undefined,
      dependencyIds: value.dependencyIds ?? [],
    };
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
        const label = account.organizationName ?? account.label ?? account.name ?? account.displayName;

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
    const dontScheduleBefore = control.get('dontScheduleBefore')?.value;

    if (!deadline || !dontScheduleBefore) {
      return null;
    }

    const deadlineDate = new Date(deadline).getTime();
    const dontScheduleBeforeDate = new Date(dontScheduleBefore).getTime();

    if (dontScheduleBeforeDate > deadlineDate) {
      return { dontScheduleBeforeAfterDeadline: true };
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
}
