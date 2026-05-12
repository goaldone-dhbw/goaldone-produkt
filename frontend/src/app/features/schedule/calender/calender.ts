import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import { CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core';
import deLocale from '@fullcalendar/core/locales/de';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import timeGridPlugin from '@fullcalendar/timegrid';
import { firstValueFrom } from 'rxjs';
import { ScheduleEntry, TaskAccountListResponse, TaskResponse, TasksService, TaskStatus } from '../../../api';
import {
  TaskEditDialogComponent,
  TaskItem,
} from '../../../shared/task-edit-dialog/task-edit-dialog.component';

export type ScheduleCalendarRange = {
  from: string;
  to: string;
};

@Component({
  selector: 'app-schedule-calendar',
  standalone: true,
  imports: [FullCalendarModule, TaskEditDialogComponent],
  templateUrl: './calender.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalenderComponent {
  private readonly tasksService = inject(TasksService);

  readonly entries = input<ScheduleEntry[]>([]);

  readonly rangeChanged = output<ScheduleCalendarRange>();
  readonly taskSaved = output<void>();

  readonly selectedTask = signal<TaskItem | null>(null);
  readonly isTaskDialogOpen = signal(false);

  readonly calendarEvents = computed<EventInput[]>(() =>
    this.entries()
      .filter((entry) => this.isValidEntry(entry))
      .map((entry, index) => this.mapScheduleEntryToEvent(entry, index)),
  );

  readonly allTaskItems = computed<TaskItem[]>(() =>
    this.entries()
      .filter((entry) => entry.type === 'TASK')
      .map((entry) => this.mapScheduleEntryToTaskItem(entry)),
  );

  readonly calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
    locale: deLocale,

    initialView: 'timeGridWeek',

    headerToolbar: {
      left: 'prev,today,next',
      center: 'title',
      right: 'timeGridWeek,timeGridDay',
    },

    buttonText: {
      today: 'Heute',
      week: 'Woche',
      day: 'Tag',
    },

    allDaySlot: false,
    nowIndicator: true,
    expandRows: true,
    height: 'auto',
    contentHeight: 'auto',

    slotMinTime: '06:00:00',
    slotMaxTime: '22:00:00',

    eventTimeFormat: {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    },

    slotLabelFormat: {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    },

    eventClassNames: (arg) => {
      const entry = arg.event.extendedProps['entry'] as ScheduleEntry | undefined;

      return entry?.type === 'TASK' ? ['cursor-pointer'] : [];
    },

    datesSet: (arg) => this.handleDatesSet(arg),
    eventClick: (arg) => this.handleEventClick(arg),
  };

  closeTaskDialog(): void {
    this.isTaskDialogOpen.set(false);
    this.selectedTask.set(null);
  }

  onTaskSaved(): void {
    this.closeTaskDialog();
    this.taskSaved.emit();
  }

  private mapScheduleEntryToEvent(entry: ScheduleEntry, index: number): EventInput {
    const colors = this.getEventColors(entry);

    return {
      id: entry.entryId ?? `${entry.occurrenceDate}-${entry.startTime}-${entry.endTime}-${index}`,
      title: this.getEntryTitle(entry),
      start: `${entry.occurrenceDate}T${entry.startTime}`,
      end: `${entry.occurrenceDate}T${entry.endTime}`,
      backgroundColor: colors.backgroundColor,
      borderColor: colors.borderColor,
      textColor: colors.textColor,
      extendedProps: {
        entry,
      },
    };
  }
  private findTaskById(
    accountLists: TaskAccountListResponse[],
    taskId: string,
  ): TaskResponse | null {
    for (const accountList of accountLists) {
      const task = accountList.tasks?.find((candidate) => candidate.id === taskId);

      if (task) {
        return task;
      }
    }

    return null;
  }

  private findAccountIdForTask(
    accountLists: TaskAccountListResponse[],
    taskId: string,
  ): string | null {
    for (const accountList of accountLists) {
      const taskExists = accountList.tasks?.some((candidate) => candidate.id === taskId);

      if (taskExists) {
        return accountList.accountId;
      }
    }

    return null;
  }

  private mapTaskResponseToTaskItem(
    task: TaskResponse,
    entry: ScheduleEntry,
    accountLists: TaskAccountListResponse[],
  ): TaskItem {
    const taskId = task.id ?? this.getTaskId(entry);

    return {
      id: taskId,
      title: task.title?.trim() || entry.originalItemTitle?.trim() || 'Unbenannte Aufgabe',
      description: task.description ?? null,
      duration: task.duration ?? this.calculateDurationInMinutes(entry),
      deadline: task.deadline ?? null,
      status: task.status ?? this.getTaskStatus(entry),
      accountId: this.findAccountIdForTask(accountLists, taskId),
      accountLabel: null,
      dependencyIds: task.dependencyIds ?? [],
      cognitiveLoad: task.cognitiveLoad ?? null,
      dontScheduleBefore: task.dontScheduleBefore ?? null,
      customChunkSize: task.customChunkSize ?? null,
    };
  }
  private getEntryTitle(entry: ScheduleEntry): string {
    const fallback =
      entry.type === 'TASK' ? 'Unbenannte Aufgabe' : entry.isBreak ? 'Pause' : 'Termin';

    const title = entry.originalItemTitle?.trim() || fallback;

    const chunkIndex = entry.chunkIndex;
    const totalChunks = entry.totalChunks;

    if (
      entry.type === 'TASK' &&
      chunkIndex !== null &&
      chunkIndex !== undefined &&
      totalChunks !== null &&
      totalChunks !== undefined &&
      totalChunks > 1
    ) {
      return `${title} (${chunkIndex + 1}/${totalChunks})`;
    }

    return title;
  }

  private mapScheduleEntryToTaskItem(entry: ScheduleEntry): TaskItem {
    const duration = this.calculateDurationInMinutes(entry);

    return {
      id: this.getTaskId(entry),
      title: entry.originalItemTitle?.trim() || 'Unbenannte Aufgabe',
      description: this.getOptionalString(entry, 'description'),
      duration,
      deadline: this.getOptionalString(entry, 'deadline'),
      status: this.getTaskStatus(entry),
      accountId: this.getOptionalString(entry, 'accountId'),
      accountLabel:
        this.getOptionalString(entry, 'accountLabel') ||
        this.getOptionalString(entry, 'organizationName') ||
        this.getOptionalString(entry, 'organizationLabel'),
      dependencyIds: this.getDependencyIds(entry),
      cognitiveLoad: this.getOptionalCognitiveLoad(entry),
      dontScheduleBefore:
        this.getOptionalString(entry, 'dontScheduleBefore') ||
        this.getOptionalString(entry, 'notBefore'),
      customChunkSize: this.getOptionalNumber(entry, 'customChunkSize'),
    };
  }

  private getTaskId(entry: ScheduleEntry): string {
    const rawEntry = entry as any;

    return (
      rawEntry.originalItemId ??
      rawEntry.taskId ??
      rawEntry.originalTaskId ??
      entry.entryId ??
      ''
    );
  }

  private getTaskStatus(entry: ScheduleEntry): TaskStatus {
    const rawEntry = entry as any;

    if (rawEntry.status === 'OPEN' || rawEntry.status === 'IN_PROGRESS' || rawEntry.status === 'DONE') {
      return rawEntry.status;
    }

    return entry.isCompleted ? 'DONE' : 'OPEN';
  }

  private getOptionalCognitiveLoad(entry: ScheduleEntry): TaskItem['cognitiveLoad'] {
    const rawEntry = entry as any;
    const value = rawEntry.cognitiveLoad;

    if (value === 'LOW' || value === 'MODERATE' || value === 'HIGH') {
      return value;
    }

    return null;
  }

  private getDependencyIds(entry: ScheduleEntry): string[] {
    const rawEntry = entry as any;
    const value = rawEntry.dependencyIds;

    return Array.isArray(value) ? value : [];
  }

  private getOptionalString(entry: ScheduleEntry, fieldName: string): string | null {
    const value = (entry as any)[fieldName];

    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }

    return value;
  }

  private getOptionalNumber(entry: ScheduleEntry, fieldName: string): number | null {
    const value = (entry as any)[fieldName];

    if (typeof value !== 'number') {
      return null;
    }

    return value;
  }

  private calculateDurationInMinutes(entry: ScheduleEntry): number {
    if (!entry.occurrenceDate || !entry.startTime || !entry.endTime) {
      return 0;
    }

    const start = new Date(`${entry.occurrenceDate}T${entry.startTime}`);
    const end = new Date(`${entry.occurrenceDate}T${entry.endTime}`);

    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      return 0;
    }

    return Math.round((end.getTime() - start.getTime()) / 60000);
  }

  private getEventColors(entry: ScheduleEntry): {
    backgroundColor: string;
    borderColor: string;
    textColor: string;
  } {
    if (entry.isCompleted) {
      return {
        backgroundColor: '#16a34a',
        borderColor: '#15803d',
        textColor: '#ffffff',
      };
    }

    if (entry.isPinned) {
      return {
        backgroundColor: '#d97706',
        borderColor: '#b45309',
        textColor: '#ffffff',
      };
    }

    if (entry.type === 'APPOINTMENT' && entry.isBreak) {
      return {
        backgroundColor: '#059669',
        borderColor: '#047857',
        textColor: '#ffffff',
      };
    }

    if (entry.type === 'APPOINTMENT') {
      return {
        backgroundColor: '#64748b',
        borderColor: '#475569',
        textColor: '#ffffff',
      };
    }

    return {
      backgroundColor: '#4f46e5',
      borderColor: '#4338ca',
      textColor: '#ffffff',
    };
  }

  private handleDatesSet(arg: DatesSetArg): void {
    this.rangeChanged.emit({
      from: this.toIsoDate(arg.start),
      to: this.toIsoDate(arg.end),
    });
  }

  private async handleEventClick(arg: EventClickArg): Promise<void> {
    arg.jsEvent.preventDefault();

    const entry = arg.event.extendedProps['entry'] as ScheduleEntry | undefined;

    if (!entry || entry.type !== 'TASK') {
      return;
    }

    const taskId = this.getTaskId(entry);

    if (!taskId) {
      this.selectedTask.set(this.mapScheduleEntryToTaskItem(entry));
      this.isTaskDialogOpen.set(true);
      return;
    }

    try {
      const accountLists = await firstValueFrom(this.tasksService.getTasksForAllAccounts());
      const task = this.findTaskById(accountLists, taskId);

      if (!task) {
        this.selectedTask.set(this.mapScheduleEntryToTaskItem(entry));
        this.isTaskDialogOpen.set(true);
        return;
      }

      this.selectedTask.set(this.mapTaskResponseToTaskItem(task, entry, accountLists));
      this.isTaskDialogOpen.set(true);
    } catch {
      this.selectedTask.set(this.mapScheduleEntryToTaskItem(entry));
      this.isTaskDialogOpen.set(true);
    }
  }

  private isValidEntry(entry: ScheduleEntry): boolean {
    if (!entry.occurrenceDate || !entry.startTime || !entry.endTime) {
      return false;
    }

    const start = new Date(`${entry.occurrenceDate}T${entry.startTime}`);
    const end = new Date(`${entry.occurrenceDate}T${entry.endTime}`);

    return !Number.isNaN(start.getTime()) && !Number.isNaN(end.getTime()) && end > start;
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  }
}
