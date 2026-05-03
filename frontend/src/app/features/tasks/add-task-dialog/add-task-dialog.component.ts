import {
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ChangeDetectionStrategy,
  inject,
  computed,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { CognitiveLoad, TaskCreateRequest, TaskResponse, TaskStatus, TaskUpdateRequest } from '../../../api';
import { AuthService } from '../../../core/auth/auth.service';
import { OrgContextService } from '../../../core/services/org-context.service';
import { Dropdown } from 'primeng/dropdown';
import { Button } from 'primeng/button';

type DependencyOption = {
  id: string;
  title: string;
};

type OrgOption = {
  id: string;
  slug: string;
  role: string;
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

/**
 * Dialog component for creating and editing tasks with optional org selection.
 *
 * - Shows org dropdown only if user is member of 2+ orgs (D-08)
 * - Selected org persists within dialog and clears on close (D-09)
 */
@Component({
  selector: 'app-add-task-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Dropdown, Button],
  templateUrl: './add-task-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddTaskDialogComponent implements OnInit, OnDestroy {
  @Input() isOpen = false;
  @Input() isEditing = false;
  @Input() editingTaskId: string | null = null;
  @Input() existingTasks: TaskResponse[] = [];
  @Input() availableDependencyOptions: DependencyOption[] = [];

  @Output() save = new EventEmitter<TaskCreateRequest | TaskUpdateRequest>();
  @Output() closed = new EventEmitter<void>();

  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly orgContextService = inject(OrgContextService);

  readonly userOrgs = signal<OrgOption[]>([]);
  readonly showOrgDropdown = signal(false);
  readonly selectedOrg = new FormControl<string | null>(null);

  readonly statuses: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE'];
  readonly cognitiveLoads: CognitiveLoad[] = ['LOW', 'MODERATE', 'HIGH'];

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

  readonly popupTitle = computed(() =>
    this.isEditing ? 'Aufgabe bearbeiten' : 'Aufgabe erstellen',
  );

  readonly popupConfirmLabel = computed(() =>
    this.isEditing ? 'Änderungen speichern' : 'Aufgabe speichern',
  );

  ngOnInit(): void {
    // Clear previous dialog org context (D-09)
    this.orgContextService.clearDialogOrg();

    // Load user orgs
    this.userOrgs.set(this.authService.getOrganizations());

    // Determine if dropdown should show
    this.showOrgDropdown.set(this.orgContextService.hasMultipleOrgs());
  }

  ngOnDestroy(): void {
    // Clear dialog context on close
    this.orgContextService.clearDialogOrg();
  }

  onOrgSelected(orgId: string): void {
    this.orgContextService.setDialogOrg(orgId);
  }

  onClose(): void {
    this.orgContextService.clearDialogOrg();
    this.closed.emit();
  }

  resetForm(): void {
    this.taskForm.reset({
      id: null,
      title: '',
      description: '',
      duration: null,
      deadline: '',
      status: 'OPEN',
      accountId: '',
      dependencyIds: [],
      cognitiveLoad: '',
      dontScheduleBefore: '',
      customChunkSize: null,
    });
    this.selectedOrg.reset(null);
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

  onSave(): void {
    this.taskForm.markAllAsTouched();

    if (this.taskForm.invalid) {
      return;
    }

    const value = this.taskForm.getRawValue() as TaskFormValue;

    if (this.isEditing) {
      const payload = this.buildUpdatePayload(value);
      this.save.emit(payload);
    } else {
      const payload = this.buildCreatePayload(value);
      this.save.emit(payload);
    }
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
