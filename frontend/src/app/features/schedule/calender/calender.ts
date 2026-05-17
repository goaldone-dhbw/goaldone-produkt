import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  OnDestroy,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FullCalendarComponent, FullCalendarModule } from '@fullcalendar/angular';
import { CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core';
import deLocale from '@fullcalendar/core/locales/de';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import timeGridPlugin from '@fullcalendar/timegrid';
import { firstValueFrom } from 'rxjs';

import {
  AppointmentCreate,
  AppointmentsService,
  ScheduleEntry,
  TaskAccountListResponse,
  TaskResponse,
  TasksService,
  TaskStatus,
} from '../../../api';
import {
  TaskEditDialogComponent,
  TaskItem,
} from '../../../shared/task-edit-dialog/task-edit-dialog.component';

import type { ScheduleWorkingTime } from '../facade/facade';
import type {
  ScheduleCompletionScope,
  ScheduleTaskCompletionEvent,
  ScheduleTaskCompletionRequest,
} from '../completion/schedule-completion-api.service';

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

type WeekdayCode = 'MO' | 'TU' | 'WE' | 'TH' | 'FR' | 'SA' | 'SU';

type AppointmentDetail = {
  id: string;
  accountId: string;
  title: string;
  kind: 'Pause' | 'Fixer Termin';
  accountLabel: string;
  date: string;
  originalStartDate: string;
  startTime: string;
  endTime: string;
  time: string;
  duration: string;
  recurrence: string;
  blocksPlanning: string;
  isBreak: boolean;
  isRecurring: boolean;
  days: WeekdayCode[];
  rule: string | null;
};

type AppointmentEditForm = {
  id: string;
  accountId: string;
  title: string;
  isBreak: boolean;
  date: string;
  startTime: string;
  endTime: string;
  isRecurring: boolean;
  days: WeekdayCode[];
};

const DEFAULT_SLOT_MIN_TIME = '06:00:00';
const DEFAULT_SLOT_MAX_TIME = '22:00:00';

const DEFAULT_WORKING_TIME_START = '08:00:00';
const DEFAULT_WORKING_TIME_END = '17:00:00';

const COMPACT_EVENT_MAX_MINUTES = 30;
const TINY_EVENT_MAX_MINUTES = 10;

const DEFAULT_WORKING_DAYS = new Set<CalendarDayName>([
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
]);

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
export class CalenderComponent implements OnDestroy {
  private readonly tasksService = inject(TasksService);
  private readonly appointmentsService = inject(AppointmentsService);
  private readonly calendarRef = viewChild(FullCalendarComponent);

  readonly entries = input<ScheduleEntry[]>([]);
  readonly workingTimes = input<ScheduleWorkingTime[]>([]);

  readonly rangeChanged = output<ScheduleCalendarRange>();
  readonly taskSaved = output<void>();
  readonly appointmentSaved = output<void>();
  readonly taskCompletionRequested = output<ScheduleTaskCompletionEvent>();

  readonly selectedTask = signal<TaskItem | null>(null);
  readonly isTaskDialogOpen = signal(false);

  readonly selectedAppointment = signal<AppointmentDetail | null>(null);
  readonly appointmentEditForm = signal<AppointmentEditForm | null>(null);
  readonly isAppointmentDialogOpen = signal(false);
  readonly isAppointmentEditMode = signal(false);
  readonly isSavingAppointment = signal(false);
  readonly appointmentError = signal('');

  readonly pendingTaskCompletionEntry = signal<ScheduleEntry | null>(null);
  readonly isTaskCompletionDialogOpen = signal(false);
  readonly isSavingTaskCompletion = signal(false);
  readonly taskCompletionError = signal('');

  readonly weekdayOptions: { value: WeekdayCode; label: string }[] = [
    { value: 'MO', label: 'Mo' },
    { value: 'TU', label: 'Di' },
    { value: 'WE', label: 'Mi' },
    { value: 'TH', label: 'Do' },
    { value: 'FR', label: 'Fr' },
    { value: 'SA', label: 'Sa' },
    { value: 'SU', label: 'So' },
  ];

  private readonly visibleRange = signal<ScheduleCalendarRange | null>(null);
  private completionCheckTimerId: ReturnType<typeof setTimeout> | null = null;

  readonly effectiveSlotRange = computed(() => this.computeEffectiveSlotRange());

  constructor() {
    effect(() => {
      const range = this.effectiveSlotRange();
      const cal = this.calendarRef();
      if (cal) {
        const api = cal.getApi();
        if (api) {
          api.setOption('slotMinTime', range.slotMinTime);
          api.setOption('slotMaxTime', range.slotMaxTime);
        }
      }
    });

    effect(() => {
      this.entries();
      this.scheduleNextTaskCompletionCheck();
    });
  }

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

    slotMinTime: DEFAULT_SLOT_MIN_TIME,
    slotMaxTime: DEFAULT_SLOT_MAX_TIME,

    eventDisplay: 'block',
    slotEventOverlap: false,
    eventMaxStack: 3,

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

      if (!entry) {
        return [];
      }

      if (entry.type === 'TASK' || entry.type === 'APPOINTMENT') {
        return ['cursor-pointer'];
      }

      return [];
    },

    datesSet: (arg) => this.handleDatesSet(arg),
    eventClick: (arg) => this.handleEventClick(arg),
  };

  closeTaskDialog(): void {
    this.isTaskDialogOpen.set(false);
    this.selectedTask.set(null);
  }

  closeAppointmentDialog(): void {
    this.isAppointmentDialogOpen.set(false);
    this.isAppointmentEditMode.set(false);
    this.selectedAppointment.set(null);
    this.appointmentEditForm.set(null);
    this.appointmentError.set('');
  }

  startAppointmentEdit(): void {
    const appointment = this.selectedAppointment();

    if (!appointment) {
      return;
    }

    this.appointmentError.set('');
    this.appointmentEditForm.set({
      id: appointment.id,
      accountId: appointment.accountId,
      title: appointment.title,
      isBreak: appointment.isBreak,
      date: appointment.originalStartDate || appointment.date,
      startTime: appointment.startTime,
      endTime: appointment.endTime,
      isRecurring: appointment.isRecurring,
      days:
        appointment.days.length > 0
          ? [...appointment.days]
          : [this.getWeekdayCodeForDate(appointment.date)],
    });

    this.isAppointmentEditMode.set(true);
  }

  cancelAppointmentEdit(): void {
    this.isAppointmentEditMode.set(false);
    this.appointmentEditForm.set(null);
    this.appointmentError.set('');
  }

  onTaskSaved(): void {
    this.closeTaskDialog();
    this.taskSaved.emit();
  }

  ngOnDestroy(): void {
    this.clearTaskCompletionTimer();
  }

  getTaskCompletionDialogTitle(): string {
    const entry = this.pendingTaskCompletionEntry();

    if (!entry) {
      return 'Aufgabe';
    }

    return this.getEntryTitle(entry);
  }

  getTaskCompletionDialogTime(): string {
    const entry = this.pendingTaskCompletionEntry();

    if (!entry) {
      return '';
    }

    return `${this.formatTime(entry.startTime)} - ${this.formatTime(entry.endTime)}`;
  }

  getTaskCompletionDialogChunkLabel(): string {
    const entry = this.pendingTaskCompletionEntry();

    if (
      !entry ||
      entry.chunkIndex === null ||
      entry.chunkIndex === undefined ||
      entry.totalChunks === null ||
      entry.totalChunks === undefined ||
      entry.totalChunks <= 1
    ) {
      return 'Dieser geplante Aufgabenblock ist abgelaufen.';
    }

    return `Chunk ${entry.chunkIndex + 1} von ${entry.totalChunks} ist abgelaufen.`;
  }

  confirmPendingTaskCompletion(scope: ScheduleCompletionScope): void {
    const entry = this.pendingTaskCompletionEntry();

    if (!entry) {
      return;
    }

    const request = this.createTaskCompletionRequest(entry, scope);

    if (!request) {
      this.taskCompletionError.set(
        'Dieser Aufgabenblock hat noch keine ScheduleEntry-ID. Das Backend muss zuerst eine eindeutige scheduleEntryId für jeden geplanten Eintrag liefern.',
      );
      return;
    }

    this.isSavingTaskCompletion.set(true);
    this.taskCompletionError.set('');

    this.taskCompletionRequested.emit({
      request,
      resolve: () => {
        this.isSavingTaskCompletion.set(false);
        this.closeTaskCompletionDialog();
        this.scheduleNextTaskCompletionCheck();
      },
      reject: (message?: string) => {
        this.isSavingTaskCompletion.set(false);
        this.taskCompletionError.set(
          message || 'Die Erledigt-Entscheidung konnte nicht gespeichert werden.',
        );
        this.scheduleNextTaskCompletionCheck();
      },
    });
  }

  private closeTaskCompletionDialog(): void {
    this.isTaskCompletionDialogOpen.set(false);
    this.pendingTaskCompletionEntry.set(null);
    this.isSavingTaskCompletion.set(false);
    this.taskCompletionError.set('');
  }

  getInputValue(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  getCheckboxValue(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  updateAppointmentEditField<K extends keyof AppointmentEditForm>(
    fieldName: K,
    value: AppointmentEditForm[K],
  ): void {
    const currentForm = this.appointmentEditForm();

    if (!currentForm) {
      return;
    }

    this.appointmentEditForm.set({
      ...currentForm,
      [fieldName]: value,
    });
  }

  updateAppointmentType(value: string): void {
    this.updateAppointmentEditField('isBreak', value === 'BREAK');
  }

  isWeekdaySelected(day: WeekdayCode): boolean {
    return this.appointmentEditForm()?.days.includes(day) ?? false;
  }

  toggleAppointmentEditDay(day: WeekdayCode, checked: boolean): void {
    const currentForm = this.appointmentEditForm();

    if (!currentForm) {
      return;
    }

    const nextDays = checked
      ? Array.from(new Set([...currentForm.days, day]))
      : currentForm.days.filter((currentDay) => currentDay !== day);

    this.appointmentEditForm.set({
      ...currentForm,
      days: nextDays,
    });
  }

  async saveAppointment(): Promise<void> {
    const form = this.appointmentEditForm();
    const appointment = this.selectedAppointment();

    if (!form) return;

    if (!form.id || !form.accountId) {
      this.appointmentError.set('Der Eintrag kann nicht gespeichert werden, weil die ID fehlt.');
      return;
    }

    if (!form.title.trim()) {
      this.appointmentError.set('Bitte gib einen Titel ein.');
      return;
    }

    if ((!form.isRecurring && !form.date) || !form.startTime || !form.endTime) {
      this.appointmentError.set('Bitte gib Datum, Startzeit und Endzeit an.');
      return;
    }

    if (this.timeToMinutes(form.endTime) <= this.timeToMinutes(form.startTime)) {
      this.appointmentError.set('Die Endzeit muss nach der Startzeit liegen.');
      return;
    }

    if (form.isRecurring && form.days.length === 0) {
      this.appointmentError.set('Bitte wähle mindestens einen Wochentag für die Wiederholung aus.');
      return;
    }

    this.isSavingAppointment.set(true);
    this.appointmentError.set('');

    try {
      if (appointment?.isRecurring === true && !appointment.isBreak) {
        const dayBefore = this.getDateBefore(appointment.date);

        const oldRule =
          appointment.rule && appointment.rule.trim()
            ? appointment.rule
            : this.createWeeklyRule(appointment.days);

        const oldPayload: AppointmentCreate = {
          title: appointment.title.trim(),
          isBreak: appointment.isBreak,
          appointmentType: 'RECURRING',
          date: form.date || null,
          startTime: appointment.startTime.substring(0, 5),
          endTime: appointment.endTime.substring(0, 5),
          rrule: this.appendUntilToRrule(oldRule, dayBefore),
        };

        await firstValueFrom(
          this.appointmentsService.updateAppointment(form.accountId, form.id, oldPayload),
        );

        const newPayload: AppointmentCreate = {
          title: form.title.trim(),
          isBreak: form.isBreak,
          appointmentType: 'RECURRING',
          date: appointment.date,
          startTime: form.startTime.substring(0, 5),
          endTime: form.endTime.substring(0, 5),
          rrule: this.createWeeklyRule(form.days),
        };

        await firstValueFrom(
          this.appointmentsService.createAppointment(form.accountId, newPayload),
        );
      } else {
        const payload = this.createAppointmentUpdatePayload(form);
        await firstValueFrom(
          this.appointmentsService.updateAppointment(form.accountId, form.id, payload),
        );
      }

      this.closeAppointmentDialog();
      this.appointmentSaved.emit();
    } catch (error) {
      console.error('saveAppointment error:', error);
      this.appointmentError.set('Der Eintrag konnte nicht gespeichert werden.');
    } finally {
      this.isSavingAppointment.set(false);
    }
  }

  private createAppointmentUpdatePayload(form: AppointmentEditForm): AppointmentCreate {
    return {
      title: form.title.trim(),
      isBreak: form.isBreak,
      appointmentType: form.isRecurring ? 'RECURRING' : 'ONE_TIME',
      date: form.isRecurring ? null : form.date || null,
      startTime: form.startTime.substring(0, 5),
      endTime: form.endTime.substring(0, 5),
      rrule: form.isRecurring ? this.createWeeklyRule(form.days) : null,
    };
  }

  private createWeeklyRule(days: WeekdayCode[]): string {
    return `FREQ=WEEKLY;BYDAY=${days.join(',')}`;
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
      return `${title || fallback} (${chunkIndex + 1}/${totalChunks})`;
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

  private mapScheduleEntryToAppointmentDetail(entry: ScheduleEntry): AppointmentDetail {
    const rawEntry = entry as any;

    const duration = this.calculateDurationInMinutes(entry);
    const rule = this.getOptionalString(entry, 'rule') || this.getOptionalString(entry, 'rrule');
    const isRecurring =
      rawEntry.source === 'RECURRING' || rawEntry.appointmentType === 'RECURRING' || Boolean(rule);

    return {
      id: String(
        entry.originalItemId ??
          (entry.entryId && !String(entry.entryId).startsWith('recurring-')
            ? entry.entryId
            : null) ??
          '',
      ),
      accountId: String(rawEntry.accountId ?? ''),
      title:
        entry.originalItemTitle?.trim() || (this.isBreakEntry(entry) ? 'Pause' : 'Fixer Termin'),
      kind: this.isBreakEntry(entry) ? 'Pause' : 'Fixer Termin',
      accountLabel:
        this.getOptionalString(entry, 'accountLabel') ||
        this.getOptionalString(entry, 'organizationName') ||
        this.getOptionalString(entry, 'organizationLabel') ||
        this.getOptionalString(entry, 'accountName') ||
        '-',
      date: entry.occurrenceDate ?? '-',
      originalStartDate:
        this.getOptionalString(entry, 'originalStartDate') ||
        (isRecurring ? '' : (entry.occurrenceDate ?? '')),
      startTime: this.formatTime(entry.startTime),
      endTime: this.formatTime(entry.endTime),
      time: `${this.formatTime(entry.startTime)} - ${this.formatTime(entry.endTime)}`,
      duration: this.formatDuration(duration),
      recurrence: isRecurring ? 'Wiederkehrend' : 'Einmalig',
      blocksPlanning: this.isBlockingEntry(entry) ? 'Ja' : 'Nein',
      isBreak: this.isBreakEntry(entry),
      isRecurring,
      days: this.parseWeekdayCodesFromRule(rule),
      rule,
    };
  }

  private getTaskId(entry: ScheduleEntry): string {
    const rawEntry = entry as any;

    return (
      rawEntry.originalItemId ?? rawEntry.taskId ?? rawEntry.originalTaskId ?? entry.entryId ?? ''
    );
  }

  private getEventClassNames(entry: ScheduleEntry): string[] {
    const classNames: string[] = [];

    if (this.isBreakEntry(entry)) {
      classNames.push('schedule-event--break');
    } else if (this.isAppointmentEntry(entry)) {
      classNames.push('schedule-event--appointment');
    } else if (entry.isCompleted) {
      classNames.push('schedule-event--completed');
    } else {
      classNames.push('schedule-event--task');
    }

    const durationInMinutes = this.getEntryDurationInMinutes(entry);

    if (
      durationInMinutes <= COMPACT_EVENT_MAX_MINUTES &&
      durationInMinutes > TINY_EVENT_MAX_MINUTES
    ) {
      classNames.push('schedule-event--compact');
    }

    if (durationInMinutes <= TINY_EVENT_MAX_MINUTES) {
      classNames.push('schedule-event--tiny');
    }

    return classNames;
  }

  private getEntryDurationInMinutes(entry: ScheduleEntry): number {
    const start = this.timeToMinutes(entry.startTime);
    const end = this.timeToMinutes(entry.endTime);

    return Math.max(end - start, 0);
  }

  private getTaskStatus(entry: ScheduleEntry): TaskStatus {
    const rawEntry = entry as any;

    if (
      rawEntry.status === 'OPEN' ||
      rawEntry.status === 'IN_PROGRESS' ||
      rawEntry.status === 'DONE'
    ) {
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

    const start = new Date(`${entry.occurrenceDate}T${this.normalizeTime(entry.startTime)}`);
    const end = new Date(`${entry.occurrenceDate}T${this.normalizeTime(entry.endTime)}`);

    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      return 0;
    }

    return Math.round((end.getTime() - start.getTime()) / 60000);
  }

  private formatDuration(minutes: number): string {
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

  private formatTime(time: string | null | undefined): string {
    const normalized = this.normalizeTime(time);
    return normalized.substring(0, 5);
  }

  private parseWeekdayCodesFromRule(rule: string | null): WeekdayCode[] {
    if (!rule) {
      return [];
    }

    const byDay = rule
      .toUpperCase()
      .split(';')
      .find((part) => part.startsWith('BYDAY='))
      ?.substring('BYDAY='.length);

    if (!byDay) {
      return [];
    }

    return byDay
      .split(',')
      .map((day) => day.trim().slice(-2))
      .filter(
        (day): day is WeekdayCode =>
          day === 'MO' ||
          day === 'TU' ||
          day === 'WE' ||
          day === 'TH' ||
          day === 'FR' ||
          day === 'SA' ||
          day === 'SU',
      );
  }

  private getWeekdayCodeForDate(date: string): WeekdayCode {
    const day = this.parseIsoDate(date).getDay();

    const map: Record<number, WeekdayCode> = {
      0: 'SU',
      1: 'MO',
      2: 'TU',
      3: 'WE',
      4: 'TH',
      5: 'FR',
      6: 'SA',
    };

    return map[day];
  }

  private computeEffectiveSlotRange(): { slotMinTime: string; slotMaxTime: string } {
    const defaultMin = this.timeToMinutes(DEFAULT_SLOT_MIN_TIME);
    const defaultMax = this.timeToMinutes(DEFAULT_SLOT_MAX_TIME);

    let earliest = defaultMin;
    let latest = defaultMax;

    for (const entry of this.entries()) {
      if (entry.startTime) {
        earliest = Math.min(earliest, this.timeToMinutes(entry.startTime));
      }
      if (entry.endTime) {
        latest = Math.max(latest, this.timeToMinutes(entry.endTime));
      }
    }

    for (const wt of this.workingTimes()) {
      if (wt.startTime) {
        earliest = Math.min(earliest, this.timeToMinutes(wt.startTime));
      }
      if (wt.endTime) {
        latest = Math.max(latest, this.timeToMinutes(wt.endTime));
      }
    }

    earliest = Math.floor(earliest / 60) * 60;
    latest = Math.ceil(latest / 60) * 60;

    return {
      slotMinTime: this.minutesToTime(earliest),
      slotMaxTime: this.minutesToTime(Math.min(latest, 24 * 60)),
    };
  }

  private getWorkingIntervalsForDate(date: string): TimeInterval[] {
    const configuredWorkingTimes = this.workingTimes();

    if (configuredWorkingTimes.length === 0) {
      return this.getDefaultWorkingIntervalsForDate(date);
    }

    const dayName = this.getDayName(date);

    const intervals = configuredWorkingTimes
      .filter((workingTime) => this.workingTimeContainsDay(workingTime, dayName))
      .map((workingTime): TimeInterval => {
        return {
          start: this.timeToMinutes(workingTime.startTime),
          end: this.timeToMinutes(workingTime.endTime),
        };
      })
      .filter((interval) => interval.end > interval.start)
      .map((interval): TimeInterval => {
        const range = this.effectiveSlotRange();
        return {
          start: Math.max(interval.start, this.timeToMinutes(range.slotMinTime)),
          end: Math.min(interval.end, this.timeToMinutes(range.slotMaxTime)),
        };
      })
      .filter((interval) => interval.end > interval.start)
      .sort((a, b) => a.start - b.start);

    return this.mergeIntervals(intervals);
  }

  private getDefaultWorkingIntervalsForDate(date: string): TimeInterval[] {
    const dayName = this.getDayName(date);

    if (!DEFAULT_WORKING_DAYS.has(dayName)) {
      return [];
    }

    const range = this.effectiveSlotRange();
    const interval: TimeInterval = {
      start: Math.max(
        this.timeToMinutes(DEFAULT_WORKING_TIME_START),
        this.timeToMinutes(range.slotMinTime),
      ),
      end: Math.min(
        this.timeToMinutes(DEFAULT_WORKING_TIME_END),
        this.timeToMinutes(range.slotMaxTime),
      ),
    };

    if (interval.end <= interval.start) {
      return [];
    }

    return [interval];
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
    const range = this.effectiveSlotRange();
    const slotStart = this.timeToMinutes(range.slotMinTime);
    const slotEnd = this.timeToMinutes(range.slotMaxTime);

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

  private scheduleNextTaskCompletionCheck(): void {
    this.clearTaskCompletionTimer();

    const delay = this.getDelayUntilNextTaskCompletionCheck();

    this.completionCheckTimerId = setTimeout(() => {
      this.checkForDueTaskCompletionPrompt();
      this.scheduleNextTaskCompletionCheck();
    }, delay);
  }

  private clearTaskCompletionTimer(): void {
    if (this.completionCheckTimerId) {
      clearTimeout(this.completionCheckTimerId);
      this.completionCheckTimerId = null;
    }
  }

  private getDelayUntilNextTaskCompletionCheck(): number {
    if (this.isTaskCompletionDialogOpen()) {
      return 30_000;
    }

    const now = new Date();
    const nextRelevantEntry = this.getNextRelevantTaskCompletionEntry(now, true);

    if (!nextRelevantEntry) {
      return 60_000;
    }

    const endTime = this.getEntryEndDate(nextRelevantEntry).getTime();
    const delay = endTime - now.getTime();

    return Math.min(Math.max(delay, 0), 60_000);
  }

  private checkForDueTaskCompletionPrompt(): void {
    if (this.isTaskCompletionDialogOpen()) {
      return;
    }

    const nextDueEntry = this.getNextRelevantTaskCompletionEntry(new Date(), false);

    if (!nextDueEntry) {
      return;
    }

    this.pendingTaskCompletionEntry.set(nextDueEntry);
    this.taskCompletionError.set('');
    this.isTaskCompletionDialogOpen.set(true);
  }

  private getNextRelevantTaskCompletionEntry(
    now: Date,
    includeFutureEntries: boolean,
  ): ScheduleEntry | null {
    const candidates = this.entries()
      .filter((entry) => entry.type === 'TASK')
      .filter((entry) => this.isValidEntry(entry))
      .filter((entry) => this.hasScheduleEntryId(entry))
      .filter((entry) => !this.isTaskEntryCompleted(entry))
      .filter((entry) => {
        if (includeFutureEntries) {
          return true;
        }

        return this.getEntryEndDate(entry).getTime() <= now.getTime();
      })
      .sort((first, second) => {
        return this.getEntryEndDate(first).getTime() - this.getEntryEndDate(second).getTime();
      });

    return candidates[0] ?? null;
  }

  private isTaskEntryCompleted(entry: ScheduleEntry): boolean {
    const rawEntry = entry as any;

    return entry.isCompleted === true || rawEntry.status === 'DONE';
  }

  private getEntryEndDate(entry: ScheduleEntry): Date {
    return new Date(`${entry.occurrenceDate}T${this.normalizeTime(entry.endTime)}`);
  }

  private createTaskCompletionRequest(
    entry: ScheduleEntry,
    scope: ScheduleCompletionScope,
  ): ScheduleTaskCompletionRequest | null {
    const scheduleEntryId = this.getScheduleEntryId(entry);

    if (!scheduleEntryId) {
      return null;
    }

    return {
      scope,
      scheduleEntryId,
    };
  }

  private hasScheduleEntryId(entry: ScheduleEntry): boolean {
    return this.getScheduleEntryId(entry) !== null;
  }

  private getScheduleEntryId(entry: ScheduleEntry): string | null {
    if (entry.entryId === null || entry.entryId === undefined) {
      return null;
    }

    const value = String(entry.entryId).trim();

    return value.length > 0 ? value : null;
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

    if (!entry) {
      return;
    }

    if (entry.type === 'TASK') {
      this.closeAppointmentDialog();
      await this.openTaskDialog(entry);
      return;
    }

    if (entry.type === 'APPOINTMENT') {
      this.closeTaskDialog();
      this.selectedAppointment.set(this.mapScheduleEntryToAppointmentDetail(entry));
      this.isAppointmentEditMode.set(false);
      this.appointmentEditForm.set(null);
      this.appointmentError.set('');
      this.isAppointmentDialogOpen.set(true);
    }
  }

  private async openTaskDialog(entry: ScheduleEntry): Promise<void> {
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
    return entry.isBreak;
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

  private getDateBefore(date: string): string {
    const [y, m, d] = date.split('-').map(Number);
    const day = new Date(y, m - 1, d);
    day.setDate(day.getDate() - 1);
    return this.toIsoDate(day);
  }

  private appendUntilToRrule(rrule: string, untilDate: string): string {
    const withoutUntil = rrule
      .split(';')
      .filter((part) => !part.toUpperCase().startsWith('UNTIL='))
      .join(';');

    const compact = untilDate.replaceAll('-', '');

    return `${withoutUntil};UNTIL=${compact}`;
  }
}
