import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ProgressBarModule } from 'primeng/progressbar';
import { TagModule } from 'primeng/tag';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageModule } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { forkJoin } from 'rxjs';
import { TaskResponse, TaskStatus } from '../../api';
import { TasksService } from '../../api';
import { UserAccountsService } from '../../api';
import { TaskEditDialogComponent, TaskItem } from '../../shared/task-edit-dialog/task-edit-dialog.component';

@Component({
  selector: 'app-mainpage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    CardModule,
    ProgressBarModule,
    TagModule,
    SkeletonModule,
    MessageModule,
    ButtonModule,
    TooltipModule,
    TaskEditDialogComponent,
  ],
  templateUrl: './mainpage.html',
  styleUrl: './mainpage.scss',
})
export class MainPage implements OnInit {
  private tasksService = inject(TasksService);
  private userAccountsService = inject(UserAccountsService);

  readonly loading = signal(true);
  readonly firstName = signal<string>('');
  readonly lastName = signal<string>('');
  readonly allTasks = signal<TaskResponse[]>([]);
  isEditDialogOpen = signal(false);
  editingTask = signal<TaskItem | null>(null);

  allTasksAsItems = computed(() => this.allTasks().map((t) => this.toTaskItem(t)));

  allUpcomingTasks = computed(() =>
    this.allTasks()
      .filter((t) => t.status !== TaskStatus.Done)
      .sort((a, b) => {
        if (!a.deadline) return 1;
        if (!b.deadline) return -1;
        return a.deadline.localeCompare(b.deadline);
      }),
  );

  upcomingTasks = computed(() => this.allUpcomingTasks().slice(0, 3));

  nextTask = computed(() => this.upcomingTasks()[0] ?? null);

  completedTasks = computed(() => this.allTasks().filter((t) => t.status === TaskStatus.Done));

  progressPercent = computed(() => {
    const total = this.allTasks().length;
    return total === 0 ? 0 : Math.round((this.completedTasks().length / total) * 100);
  });

  completedMinutes = computed(() =>
    this.completedTasks().reduce((sum, t) => sum + (t.duration ?? 0), 0),
  );

  completedTasksCount = computed(() => this.completedTasks().length);

  totalTasksCount = computed(() => this.allTasks().length);

  formatDuration(minutes: number): string {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    if (hours === 0) return `${mins}m`;
    if (mins === 0) return `${hours}h`;
    return `${hours}h ${mins}m`;
  }

  formatDate(dateString: string | null | undefined): string {
    if (!dateString) return 'Kein Datum';
    try {
      const date = new Date(dateString);
      const day = String(date.getDate()).padStart(2, '0');
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const year = date.getFullYear();
      return `${day}.${month}.${year}`;
    } catch {
      return 'Kein Datum';
    }
  }

  getTaskStatusSeverity(
    status: TaskStatus | undefined,
  ): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    switch (status) {
      case TaskStatus.Open:
        return 'warn';
      case TaskStatus.InProgress:
        return 'info';
      case TaskStatus.Done:
        return 'success';
      default:
        return 'secondary';
    }
  }

  editTask(taskId: string | undefined): void {
    if (!taskId) return;
    const task = this.allTasks().find((t) => t.id === taskId);
    if (!task) return;
    this.editingTask.set(this.toTaskItem(task));
    this.isEditDialogOpen.set(true);
  }

  onTaskSaved(): void {
    void this.loadTasks();
  }

  private loadTasks(): void {
    this.tasksService.getTasksForAllAccounts().subscribe((result) => {
      this.allTasks.set(result.flatMap((tl) => tl.tasks ?? []));
    });
  }

  private toTaskItem(t: TaskResponse): TaskItem {
    return {
      id: t.id!,
      title: t.title!,
      description: t.description ?? null,
      duration: t.duration ?? 0,
      deadline: t.deadline ?? null,
      status: t.status!,
      accountId: t.accountId ?? null,
      accountLabel: null,
      dependencyIds: t.dependencyIds ?? [],
      cognitiveLoad: t.cognitiveLoad ?? null,
      dontScheduleBefore: t.dontScheduleBefore ?? null,
      customChunkSize: t.customChunkSize ?? null,
    };
  }

  markTaskDone(task: TaskResponse | null): void {
    if (!task || !task.id) return;
    const updatedTask = { ...task, status: TaskStatus.Done };
    this.tasksService.updateTask(task.id, updatedTask).subscribe(() => {
      const updated = this.allTasks().map((t) => (t.id === task.id ? updatedTask : t));
      this.allTasks.set(updated);
    });
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

  formatCognitiveLoad(load: string | null | undefined): string {
    switch (load) {
      case 'LOW':
        return 'Niedrig';
      case 'MODERATE':
        return 'Mittel';
      case 'HIGH':
        return 'Hoch';
      default:
        return load ?? '-';
    }
  }

  getStatusBadgeClass(status: TaskStatus | null | undefined): string {
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

  getCognitiveLoadBadgeClass(load: string | null | undefined): string {
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

  ngOnInit(): void {
    forkJoin({
      accounts: this.userAccountsService.getMyAccounts(),
      tasks: this.tasksService.getTasksForAllAccounts(),
    }).subscribe((result) => {
      this.firstName.set(result.accounts.accounts[0]?.firstName ?? '');
      this.lastName.set(result.accounts.accounts[0]?.lastName ?? '');
      this.allTasks.set(result.tasks.flatMap((tl) => tl.tasks ?? []));
      this.loading.set(false);
    });
  }

  protected readonly TaskStatus = TaskStatus;
}
