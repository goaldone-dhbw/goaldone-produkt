import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Button } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import {
  CognitiveLoad,
  TaskResponse,
  TaskStatus,
  TaskUpdateRequest,
} from '../../api';
import { TasksService } from '../../api';
import { UserAccountsService } from '../../api';
import { BasePopupComponent } from '../../shared/base-popup/base-popup.component';
import { TaskEditDialogComponent, TaskItem } from '../../shared/task-edit-dialog/task-edit-dialog.component';

type AccountOption = {
  id: string;
  label: string;
};

@Component({
  selector: 'app-tasks-page',
  standalone: true,
  imports: [CommonModule, Button, Tooltip, TaskEditDialogComponent, BasePopupComponent],
  templateUrl: './tasks-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksPageComponent {
  private readonly tasksService = inject(TasksService);
  private readonly userAccountsService = inject(UserAccountsService);

  readonly tasks = signal<TaskItem[]>([]);
  readonly accounts = signal<AccountOption[]>([]);

  readonly isLoading = signal(false);
  readonly isDeleting = signal(false);

  readonly listErrorMessage = signal('');
  readonly successMessage = signal('');

  isTaskPopupOpen = signal(false);
  isDeletePopupOpen = signal(false);

  editingTask = signal<TaskItem | null>(null);
  deletingTask = signal<TaskItem | null>(null);

  readonly hasMultipleAccounts = computed(() => this.accounts().length > 1);
  readonly hasSingleAccount = computed(() => this.accounts().length === 1);

  readonly currentAccount = computed<AccountOption | null>(() => {
    const accounts = this.accounts();
    return accounts.length === 1 ? accounts[0] : null;
  });

  readonly currentAccountLabel = computed(() => this.currentAccount()?.label ?? null);

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

  private getReadableErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (typeof error.error === 'string' && error.error.trim()) {
      return error.error;
    }

    return error.error?.message || error.error?.detail || error.error?.error || fallback;
  }
}
