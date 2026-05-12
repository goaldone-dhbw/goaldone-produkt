import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import { CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core';
import deLocale from '@fullcalendar/core/locales/de';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import timeGridPlugin from '@fullcalendar/timegrid';

import { ScheduleEntry } from '../../../api';

export type ScheduleCalendarRange = {
  from: string;
  to: string;
};

@Component({
  selector: 'app-schedule-calendar',
  standalone: true,
  imports: [FullCalendarModule],
  templateUrl: './calender.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CalenderComponent {
  readonly entries = input<ScheduleEntry[]>([]);

  readonly rangeChanged = output<ScheduleCalendarRange>();

  readonly calendarEvents = computed<EventInput[]>(() =>
    this.entries()
      .filter((entry) => this.isValidEntry(entry))
      .map((entry, index) => this.mapScheduleEntryToEvent(entry, index)),
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

    datesSet: (arg) => this.handleDatesSet(arg),
    eventClick: (arg) => this.handleEventClick(arg),
  };

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
      display: 'block',
      extendedProps: {
        entry,
        isBreak: this.isBreakEntry(entry),
      },
    };
  }

  private getEntryTitle(entry: ScheduleEntry): string {
    if (this.isBreakEntry(entry)) {
      return entry.originalItemTitle?.trim() || 'Pause';
    }

    const fallback = entry.type === 'TASK' ? 'Unbenannte Aufgabe' : 'Termin';

    const chunkSuffix =
      entry.type === 'TASK' &&
      entry.chunkIndex !== null &&
      entry.chunkIndex !== undefined &&
      entry.totalChunks
        ? ` (${entry.chunkIndex + 1}/${entry.totalChunks})`
        : '';

    return `${entry.originalItemTitle?.trim() || fallback}${chunkSuffix}`;
  }

  private getEventColors(entry: ScheduleEntry): {
    backgroundColor: string;
    borderColor: string;
    textColor: string;
  } {
    if (this.isBreakEntry(entry)) {
      return {
        backgroundColor: '#06b6d4',
        borderColor: '#0891b2',
        textColor: '#ffffff',
      };
    }

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

  private handleEventClick(arg: EventClickArg): void {
    arg.jsEvent.preventDefault();
  }

  private isValidEntry(entry: ScheduleEntry): boolean {
    if (!entry.occurrenceDate || !entry.startTime || !entry.endTime) {
      return false;
    }

    const start = new Date(`${entry.occurrenceDate}T${entry.startTime}`);
    const end = new Date(`${entry.occurrenceDate}T${entry.endTime}`);

    return !Number.isNaN(start.getTime()) && !Number.isNaN(end.getTime()) && end > start;
  }

  private isBreakEntry(entry: ScheduleEntry): boolean {
    return entry.isBreak === true;
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  }
}
