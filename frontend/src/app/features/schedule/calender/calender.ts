import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import { CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core';
import deLocale from '@fullcalendar/core/locales/de';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import timeGridPlugin from '@fullcalendar/timegrid';
import { firstValueFrom } from 'rxjs';
import { ScheduleEntry, TaskAccountListResponse, TaskResponse, TasksService, TaskStatus } from '../../../api';
import { TaskEditDialogComponent, TaskItem } from '../../../shared/task-edit-dialog/task-edit-dialog.component';

import type { ScheduleWorkingTime } from '../facade/facade';

export type ScheduleCalendarRange = {
  from: string;
  to: string;
};

type TimeInterval = {
  start: number;
  end: number;
};

type CalendarDayName =
  | 'SUNDAY'
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY';

const SLOT_MIN_TIME = '06:00:00';
const SLOT_MAX_TIME = '22:00:00';

const DAY_ORDER: CalendarDayName[] = [
  'SUNDAY',
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
];

const DAY_ALIASES: Record<string, CalendarDayName> = {
  SUNDAY: 'SUNDAY',
  SUN: 'SUNDAY',
  SO: 'SUNDAY',
  SONNTAG: 'SUNDAY',

  MONDAY: 'MONDAY',
  MON: 'MONDAY',
  MO: 'MONDAY',
  MONTAG: 'MONDAY',

  TUESDAY: 'TUESDAY',
  TUE: 'TUESDAY',
  TUES: 'TUESDAY',
  DI: 'TUESDAY',
  DIENSTAG: 'TUESDAY',

  WEDNESDAY: 'WEDNESDAY',
  WED: 'WEDNESDAY',
  MI: 'WEDNESDAY',
  MITTWOCH: 'WEDNESDAY',

  THURSDAY: 'THURSDAY',
  THU: 'THURSDAY',
  THUR: 'THURSDAY',
  DO: 'THURSDAY',
  DONNERSTAG: 'THURSDAY',

  FRIDAY: 'FRIDAY',
  FRI: 'FRIDAY',
  FR: 'FRIDAY',
  FREITAG: 'FRIDAY',

  SATURDAY: 'SATURDAY',
  SAT: 'SATURDAY',
  SA: 'SATURDAY',
  SAMSTAG: 'SATURDAY',
};

@Component({
  selector: 'app-schedule-calendar',
  standalone: true,
  imports: [FullCalendarModule, TaskEditDialogComponent],
  templateUrl: './calender.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'block',
  },
})
export class CalenderComponent {
  private readonly tasksService = inject(TasksService);

  readonly entries = input<ScheduleEntry[]>([]);
  readonly workingTimes = input<ScheduleWorkingTime[]>([]);

  readonly rangeChanged = output<ScheduleCalendarRange>();
  readonly taskSaved = output<void>();

  readonly selectedTask = signal<TaskItem | null>(null);
  readonly isTaskDialogOpen = signal(false);

  private readonly visibleRange = signal<ScheduleCalendarRange | null>(null);

  readonly calendarEvents = computed<EventInput[]>(() => {
    const nonWorkingTimeEvents = this.mapNonWorkingTimesToBackgroundEvents();

    const scheduleEvents = this.entries()
      .filter((entry) => this.isValidEntry(entry))
      .map((entry, index) => this.mapScheduleEntryToEvent(entry, index));

    return [...nonWorkingTimeEvents, ...scheduleEvents];
  });

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

    slotMinTime: SLOT_MIN_TIME,
    slotMaxTime: SLOT_MAX_TIME,

    eventDisplay: 'block',

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
    return {
      id: entry.entryId ?? `${entry.occurrenceDate}-${entry.startTime}-${entry.endTime}-${index}`,
      title: this.getEntryTitle(entry),
      start: `${entry.occurrenceDate}T${this.normalizeTime(entry.startTime)}`,
      end: `${entry.occurrenceDate}T${this.normalizeTime(entry.endTime)}`,
      display: 'block',
      classNames: this.getEventClassNames(entry),
      extendedProps: {
        entry,
        isBreak: this.isBreakEntry(entry),
        isAppointment: this.isAppointmentEntry(entry),
        isBlocking: this.isBlockingEntry(entry),
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

  private mapNonWorkingTimesToBackgroundEvents(): EventInput[] {
    const range = this.visibleRange();

    if (!range) {
      return [];
    }

    const dates = this.getDatesInRange(range.from, range.to);

    return dates.flatMap((date) => {
      const workingIntervals = this.getWorkingIntervalsForDate(date);
      const nonWorkingIntervals = this.getNonWorkingIntervals(workingIntervals);

      return nonWorkingIntervals.map((interval, index): EventInput => {
        return {
          id: `non-working-${date}-${index}`,
          start: `${date}T${this.minutesToTime(interval.start)}`,
          end: `${date}T${this.minutesToTime(interval.end)}`,
          display: 'background',
          classNames: ['schedule-event--non-working'],
          extendedProps: {
            isNonWorkingTime: true,
          },
        };
      });
    });
  }

  private getEntryTitle(entry: ScheduleEntry): string {
    const title = entry.originalItemTitle?.trim();

    if (this.isBreakEntry(entry)) {
      return `Pause: ${title || 'Pause'}`;
    }

    if (this.isAppointmentEntry(entry)) {
      return `Termin: ${title || 'Fixer Termin'}`;
    }

    const fallback = entry.type === 'TASK' ? 'Unbenannte Aufgabe' : 'Termin';


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

    return title || fallback;
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

  private getEventClassNames(entry: ScheduleEntry): string[] {
    if (this.isBreakEntry(entry)) {
      return ['schedule-event--break'];
    }

    if (this.isAppointmentEntry(entry)) {
      return ['schedule-event--appointment'];
    }

    if (entry.isCompleted) {
      return ['schedule-event--completed'];
    }

    return ['schedule-event--task'];
  }

  private getWorkingIntervalsForDate(date: string): TimeInterval[] {
    const dayName = this.getDayName(date);

    const intervals = this.workingTimes()
      .filter((workingTime) => this.workingTimeContainsDay(workingTime, dayName))
      .map((workingTime): TimeInterval => {
        return {
          start: this.timeToMinutes(workingTime.startTime),
          end: this.timeToMinutes(workingTime.endTime),
        };
      })
      .filter((interval) => interval.end > interval.start)
      .map((interval): TimeInterval => {
        return {
          start: Math.max(interval.start, this.timeToMinutes(SLOT_MIN_TIME)),
          end: Math.min(interval.end, this.timeToMinutes(SLOT_MAX_TIME)),
        };
      })
      .filter((interval) => interval.end > interval.start)
      .sort((a, b) => a.start - b.start);

    return this.mergeIntervals(intervals);
  }

  private workingTimeContainsDay(
    workingTime: ScheduleWorkingTime,
    dayName: CalendarDayName,
  ): boolean {
    const normalizedDays = this.normalizeWorkingDays(workingTime.days);
    return normalizedDays.has(dayName);
  }

  private normalizeWorkingDays(days: string[] | null | undefined): Set<CalendarDayName> {
    const result = new Set<CalendarDayName>();

    if (!days) {
      return result;
    }

    for (const rawDay of days) {
      const expandedDays = this.expandDayValue(String(rawDay));

      for (const day of expandedDays) {
        result.add(day);
      }
    }

    return result;
  }

  private expandDayValue(rawValue: string): CalendarDayName[] {
    const parts = rawValue
      .split(',')
      .map((part) => part.trim())
      .filter(Boolean);

    if (parts.length > 1) {
      return parts.flatMap((part) => this.expandDayValue(part));
    }

    const value = this.normalizeDayToken(rawValue);

    if (!value) {
      return [];
    }

    const directMatch = DAY_ALIASES[value];

    if (directMatch) {
      return [directMatch];
    }

    const rangeMatch = this.expandDayRange(value);

    if (rangeMatch.length > 0) {
      return rangeMatch;
    }

    return [];
  }

  private expandDayRange(value: string): CalendarDayName[] {
    const normalizedRange = value
      .replace(/[–—]/g, '-')
      .replace(/_/g, '-')
      .replace(/BIS/g, '-')
      .replace(/TO/g, '-');

    const rangeParts = normalizedRange
      .split('-')
      .map((part) => part.trim())
      .filter(Boolean);

    if (rangeParts.length !== 2) {
      return [];
    }

    const startDay = DAY_ALIASES[rangeParts[0]];
    const endDay = DAY_ALIASES[rangeParts[1]];

    if (!startDay || !endDay) {
      return [];
    }

    const startIndex = DAY_ORDER.indexOf(startDay);
    const endIndex = DAY_ORDER.indexOf(endDay);

    if (startIndex === -1 || endIndex === -1) {
      return [];
    }

    if (startIndex <= endIndex) {
      return DAY_ORDER.slice(startIndex, endIndex + 1);
    }

    return [...DAY_ORDER.slice(startIndex), ...DAY_ORDER.slice(0, endIndex + 1)];
  }

  private normalizeDayToken(value: string): string {
    return value
      .trim()
      .toUpperCase()
      .replace(/\./g, '')
      .replace(/\s+/g, '')
      .replace(/Ä/g, 'AE')
      .replace(/Ö/g, 'OE')
      .replace(/Ü/g, 'UE');
  }

  private getNonWorkingIntervals(workingIntervals: TimeInterval[]): TimeInterval[] {
    const slotStart = this.timeToMinutes(SLOT_MIN_TIME);
    const slotEnd = this.timeToMinutes(SLOT_MAX_TIME);

    if (workingIntervals.length === 0) {
      return [{ start: slotStart, end: slotEnd }];
    }

    const nonWorkingIntervals: TimeInterval[] = [];
    let cursor = slotStart;

    for (const interval of workingIntervals) {
      if (interval.start > cursor) {
        nonWorkingIntervals.push({
          start: cursor,
          end: interval.start,
        });
      }

      cursor = Math.max(cursor, interval.end);
    }

    if (cursor < slotEnd) {
      nonWorkingIntervals.push({
        start: cursor,
        end: slotEnd,
      });
    }

    return nonWorkingIntervals.filter((interval) => interval.end > interval.start);
  }

  private mergeIntervals(intervals: TimeInterval[]): TimeInterval[] {
    if (intervals.length === 0) {
      return [];
    }

    const merged: TimeInterval[] = [];

    for (const interval of intervals) {
      const last = merged[merged.length - 1];

      if (!last || interval.start > last.end) {
        merged.push({ ...interval });
        continue;
      }

      last.end = Math.max(last.end, interval.end);
    }

    return merged;
  }

  private handleDatesSet(arg: DatesSetArg): void {
    const range = {
      from: this.toIsoDate(arg.start),
      to: this.toIsoDate(arg.end),
    };

    this.visibleRange.set(range);
    this.rangeChanged.emit(range);
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

    const start = new Date(`${entry.occurrenceDate}T${this.normalizeTime(entry.startTime)}`);
    const end = new Date(`${entry.occurrenceDate}T${this.normalizeTime(entry.endTime)}`);

    return !Number.isNaN(start.getTime()) && !Number.isNaN(end.getTime()) && end > start;
  }

  private isBreakEntry(entry: ScheduleEntry): boolean {
    return entry.isBreak === true;
  }

  private isAppointmentEntry(entry: ScheduleEntry): boolean {
    return entry.type === 'APPOINTMENT' && !this.isBreakEntry(entry);
  }

  private isBlockingEntry(entry: ScheduleEntry): boolean {
    return this.isBreakEntry(entry) || this.isAppointmentEntry(entry);
  }

  private getDatesInRange(from: string, to: string): string[] {
    const dates: string[] = [];
    const current = this.parseIsoDate(from);
    const end = this.parseIsoDate(to);

    while (current < end) {
      dates.push(this.toIsoDate(current));
      current.setDate(current.getDate() + 1);
    }

    return dates;
  }

  private getDayName(date: string): CalendarDayName {
    const dayIndex = this.parseIsoDate(date).getDay();
    return DAY_ORDER[dayIndex];
  }

  private timeToMinutes(time: string): number {
    const [hours, minutes] = this.normalizeTime(time)
      .split(':')
      .map((part) => Number(part));

    return hours * 60 + minutes;
  }

  private minutesToTime(value: number): string {
    const hours = Math.floor(value / 60);
    const minutes = value % 60;

    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
  }

  private normalizeTime(time: string | null | undefined): string {
    if (!time) {
      return '00:00:00';
    }

    const parts = time.split(':');

    const hours = parts[0]?.padStart(2, '0') ?? '00';
    const minutes = parts[1]?.padStart(2, '0') ?? '00';
    const seconds = parts[2]?.padStart(2, '0') ?? '00';

    return `${hours}:${minutes}:${seconds}`;
  }

  private parseIsoDate(value: string): Date {
    const [year, month, day] = value.split('-').map((part) => Number(part));
    return new Date(year, month - 1, day);
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  }
}
