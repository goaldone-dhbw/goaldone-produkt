import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  computed,
  inject,
  signal,
  input,
} from '@angular/core';
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
  TaskStatus,
  TaskUpdateRequest,
} from '../../api';
import { TasksService } from '../../api';
import { UserAccountsService } from '../../api';
import { BasePopupComponent } from '../base-popup/base-popup.component';

export type AccountOption = {
  id: string;
  label: string;
};

export type DependencyOption = {
  id: string;
  title: string;
};

export type TaskItem = {
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

export type TaskDialogMode = 'create' | 'edit' | 'view';

type TaskFormValue = {
  id: string | null;
  title: string;
  description: string;
  durationHours: number | null;
  durationMinutes: number | null;  deadline: string;
  status: TaskStatus;
  accountId: string;
  dependencyIds: string[];
  cognitiveLoad: CognitiveLoad | '';
  dontScheduleBefore: string;
  customChunkSize: number | null;
};

@Component({
  selector: 'app-task-edit-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, BasePopupComponent],
  templateUrl: './task-edit-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TaskEditDialogComponent implements OnInit, OnChanges {
  @Input() isOpen = false;
  @Input() task: TaskItem | null = null;
  @Input() mode: TaskDialogMode | null = null;

  readonly currentMode = signal<TaskDialogMode>('create');

  readonly allTasks = input<TaskItem[]>([]);

  @Output() isOpenChange = new EventEmitter<boolean>();
  @Output() taskSaved = new EventEmitter<void>();

  private readonly fb = inject(FormBuilder);
  private readonly tasksService = inject(TasksService);
  private readonly userAccountsService = inject(UserAccountsService);

  readonly isSaving = signal(false);
  readonly formErrorMessage = signal('');
  readonly accounts = signal<AccountOption[]>([]);
  readonly currentTaskId = signal<string | undefined>(undefined);
  readonly selectedAccountId = signal<string | null>(null);

  readonly statuses: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE'];
  readonly cognitiveLoads: CognitiveLoad[] = ['LOW', 'MODERATE', 'HIGH'];

  readonly hasMultipleAccounts = computed(() => this.accounts().length > 1);
  readonly hasSingleAccount = computed(() => this.accounts().length === 1);

  readonly currentAccount = computed<AccountOption | null>(() => {
    const accounts = this.accounts();
    return accounts.length === 1 ? accounts[0] : null;
  });

  readonly currentAccountLabel = computed(() => this.currentAccount()?.label ?? null);

  readonly effectiveMode = computed<TaskDialogMode>(() => this.currentMode());

  readonly isViewMode = computed(() => this.effectiveMode() === 'view');
  readonly isEditMode = computed(() => this.effectiveMode() === 'edit');

  readonly popupTitle = computed(() => {
    if (this.isViewMode()) {
      return this.task?.title || 'Aufgabendetails';
    }

    if (this.isEditMode()) {
      return 'Aufgabe bearbeiten';
    }

    return 'Aufgabe erstellen';
  });

  readonly popupConfirmLabel = computed(() =>
    this.isEditMode() ? 'Änderungen speichern' : 'Aufgabe speichern',
  );

  readonly taskForm = this.fb.group(
    {
      id: this.fb.control<string | null>(null),
      title: this.fb.control('', [Validators.required, Validators.maxLength(150)]),
      description: this.fb.control(''),
      durationHours: this.fb.control<number | null>(0, [
        Validators.required,
        Validators.min(0),
      ]),
      durationMinutes: this.fb.control<number | null>(0, [
        Validators.required,
        Validators.min(0),
        Validators.max(59),
      ]),
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
    const currentId = this.currentTaskId();
    const selectedAccountId = this.selectedAccountId();

    return this.allTasks()
      .filter((task) => task.id !== currentId && task.accountId === selectedAccountId)
      .map((task) => ({
        id: task.id,
        title: this.buildDependencyOptionLabel(task),
      }));
  });

  constructor() {
    void this.loadAccounts();
  }

  ngOnInit(): void {
    this.taskForm.get('accountId')?.valueChanges.subscribe((accountId) => {
      this.selectedAccountId.set(accountId);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['task']) {
      this.currentTaskId.set(this.task?.id);
    }

    // Reset form wenn task sich ändert
    if (
      changes['task'] ||
      changes['mode'] ||
      (changes['isOpen'] && this.isOpen)
    ) {
      this.currentMode.set(this.resolveInitialMode());
    }

    if (changes['task'] && !changes['task'].firstChange) {
      this.resetFormForTask();
    }
    // Reset form wenn dialog geöffnet wird (bei neuem Öffnen)
    if (changes['isOpen'] && this.isOpen && !changes['isOpen'].firstChange) {
      this.resetFormForTask();
    }
  }

  async loadAccounts(): Promise<void> {
    try {
      const response = await firstValueFrom(this.userAccountsService.getMyAccounts());
      const normalized = this.normalizeAccountResponse(response.accounts);
      this.accounts.set(normalized);

      if (normalized.length === 1) {
        const accountId = normalized[0].id;
        this.taskForm.patchValue({ accountId });
        this.selectedAccountId.set(accountId);
      }
    } catch {
      this.accounts.set([]);
    }
  }

  enableEditMode(): void {
    if (!this.task) {
      return;
    }

    this.currentMode.set('edit');
  }

  save(): void {
    if (this.isViewMode()) {
      return;
    }

    this.formErrorMessage.set('');
    this.taskForm.markAllAsTouched();

    if (this.taskForm.invalid) {
      this.formErrorMessage.set('Bitte fülle alle Pflichtfelder korrekt aus.');
      return;
    }

    const value = this.taskForm.getRawValue() as TaskFormValue;
    const duration = this.getDurationInMinutes(value);

    if (duration <= 0) {
      this.formErrorMessage.set('Bitte gib eine Dauer von mindestens einer Minute an.');
      return;
    }

    this.isSaving.set(true);

    void this.saveTaskInternal(value);
  }

  cancel(): void {
    this.isOpenChange.emit(false);
  }
  isDependencySelected(id: string): boolean {
    const current = this.taskForm.get('dependencyIds')?.value ?? [];
    return current.includes(id);
  }

  selectDependency(id: string, event: Event): void {
    event.preventDefault();
    const current = this.taskForm.get('dependencyIds')?.value ?? [];
    const updated = current.includes(id) ? [] : [id];
    this.taskForm.get('dependencyIds')?.setValue(updated);
  }

  showFieldError(
    fieldName: 'title' | 'durationHours' | 'durationMinutes' | 'accountId' | 'customChunkSize',): boolean {    const control = this.taskForm.get(fieldName);
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

  formatDurationForView(minutes: number | null): string {
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

  formatDependencyTitlesForView(task: TaskItem): string {
    if (!task.dependencyIds?.length) {
      return '-';
    }

    const titleMap = new Map(this.allTasks().map((item) => [item.id, item.title]));

    const titles = task.dependencyIds
      .map((id) => titleMap.get(id))
      .filter((title): title is string => !!title);

    return titles.length ? titles.join(', ') : `${task.dependencyIds.length} ausgewählt`;
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

  private resetFormForTask(): void {
    this.formErrorMessage.set('');

    if (this.task) {
      const accountId = this.task.accountId ?? this.getDefaultAccountId();
      this.taskForm.reset({
        id: this.task.id,
        title: this.task.title,
        description: this.task.description ?? '',
        durationHours: Math.floor(this.task.duration / 60),
        durationMinutes: this.task.duration % 60,
        deadline: this.toDateTimeLocalValue(this.task.deadline),
        status: this.task.status,
        accountId,
        dependencyIds: this.task.dependencyIds ?? [],
        cognitiveLoad: this.task.cognitiveLoad ?? '',
        dontScheduleBefore: this.toDateTimeLocalValue(this.task.dontScheduleBefore),
        customChunkSize: this.task.customChunkSize,
      });
      this.selectedAccountId.set(accountId);
    } else {
      const accountId = this.getDefaultAccountId();
      this.taskForm.reset({
        id: null,
        title: '',
        description: '',
        durationHours: 0,
        durationMinutes: 0,
        deadline: '',
        status: 'OPEN',
        accountId,
        dependencyIds: [],
        cognitiveLoad: '',
        dontScheduleBefore: '',
        customChunkSize: null,
      });
      this.selectedAccountId.set(accountId);
    }
  }

  private async saveTaskInternal(value: TaskFormValue): Promise<void> {
    try {
      if (this.task) {
        const taskId = this.task.id;
        const payload = this.buildUpdatePayload(value);
        await firstValueFrom(this.tasksService.updateTask(taskId, payload));
      } else {
        const payload = this.buildCreatePayload(value);
        await firstValueFrom(this.tasksService.createTask(payload));
      }

      this.isOpenChange.emit(false);
      this.taskSaved.emit();
    } catch (error) {
      this.formErrorMessage.set(
        this.getReadableErrorMessage(error, 'Die Aufgabe konnte nicht gespeichert werden.'),
      );
    } finally {
      this.isSaving.set(false);
    }
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
      duration: this.getDurationInMinutes(value),
      deadline: value.deadline ? new Date(value.deadline).toISOString() : undefined,
      status: value.status,
      cognitiveLoad: (value.cognitiveLoad || 'MODERATE') as CognitiveLoad,
      dontScheduleBefore: value.dontScheduleBefore
        ? new Date(value.dontScheduleBefore).toISOString()
        : undefined,
      customChunkSize: value.customChunkSize ? Number(value.customChunkSize) : undefined,
      dependencyIds: value.dependencyIds ?? [],
    };
  }

  private buildUpdatePayload(value: TaskFormValue): TaskUpdateRequest {
    return {
      title: value.title.trim(),
      description: value.description?.trim() || undefined,
      duration: this.getDurationInMinutes(value),
      deadline: value.deadline ? new Date(value.deadline).toISOString() : undefined,
      status: value.status,
      cognitiveLoad: (value.cognitiveLoad || 'MODERATE') as CognitiveLoad,
      dontScheduleBefore: value.dontScheduleBefore
        ? new Date(value.dontScheduleBefore).toISOString()
        : undefined,
      customChunkSize: value.customChunkSize ? Number(value.customChunkSize) : undefined,
      dependencyIds: value.dependencyIds ?? [],
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
    const durationHours = Number(control.get('durationHours')?.value ?? 0);
    const durationMinutes = Number(control.get('durationMinutes')?.value ?? 0);
    const duration = durationHours * 60 + durationMinutes;
    const customChunkSize = Number(control.get('customChunkSize')?.value);

    if (!customChunkSize) {
      return null;
    }

    if (customChunkSize <= 0 || (duration > 0 && customChunkSize > duration)) {
      return { chunkSizeInvalid: true };
    }

    return null;
  }

  private resolveInitialMode(): TaskDialogMode {
    if (this.mode) {
      return this.mode;
    }

    return this.task ? 'edit' : 'create';
  }
  private getDurationInMinutes(value: TaskFormValue): number {
    const hours = Number(value.durationHours ?? 0);
    const minutes = Number(value.durationMinutes ?? 0);

    return hours * 60 + minutes;
  }
}
