import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CardModule } from 'primeng/card';
import { ProgressBarModule } from 'primeng/progressbar';
import { TagModule } from 'primeng/tag';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageModule } from 'primeng/message';
import { forkJoin } from 'rxjs';
import { TaskResponse, TaskStatus } from '../../api';
import { TasksService } from '../../api';
import { UserAccountsService } from '../../api';

@Component({
  selector: 'app-mainpage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    CardModule,
    ProgressBarModule,
    TagModule,
    SkeletonModule,
    MessageModule,
  ],
  templateUrl: './mainpage.html',
  styleUrl: './mainpage.scss',
})
export class MainPage implements OnInit {
  private tasksService = inject(TasksService);
  private userAccountsService = inject(UserAccountsService);

  loading = signal(true);
  firstName = signal<string>('');
  lastName = signal<string>('');
  allTasks = signal<TaskResponse[]>([]);

  upcomingTasks = computed(() =>
    this.allTasks()
      .filter((t) => t.status !== TaskStatus.Done)
      .sort((a, b) => {
        if (!a.deadline) return 1;
        if (!b.deadline) return -1;
        return a.deadline.localeCompare(b.deadline);
      })
  );

  nextTask = computed(() => this.upcomingTasks()[0] ?? null);

  completedTasks = computed(() =>
    this.allTasks().filter((t) => t.status === TaskStatus.Done)
  );

  progressPercent = computed(() => {
    const total = this.allTasks().length;
    return total === 0 ? 0 : Math.round((this.completedTasks().length / total) * 100);
  });

  completedMinutes = computed(() =>
    this.completedTasks().reduce((sum, t) => sum + (t.duration ?? 0), 0)
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

  getTaskStatusSeverity(status: TaskStatus | undefined): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
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
}
