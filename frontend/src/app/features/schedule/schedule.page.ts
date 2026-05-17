import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { ProgressBar } from 'primeng/progressbar';

import { CalenderComponent, ScheduleCalendarRange } from './calender/calender';
import { ScheduleFacadeService } from './facade/facade';
import { Toast } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { AccountStateService } from '../../core/services/account-state.service';
import type { ScheduleTaskCompletionEvent } from './completion/schedule-completion-api.service';

@Component({
  selector: 'app-schedule',
  standalone: true,
  imports: [CommonModule, RouterLink, Button, ProgressBar, Toast, CalenderComponent],
  providers: [MessageService],
  templateUrl: './schedule.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SchedulePage {
  readonly facade = inject(ScheduleFacadeService);
  readonly accountStateService = inject(AccountStateService);
  private readonly messageService = inject(MessageService);

  constructor() {
    this.accountStateService.refresh();
    void this.facade.initialize();
  }

  async onAccountChanged(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    await this.facade.selectAccount(select.value);
  }

  async onGenerateSchedule(): Promise<void> {
    if (this.accountStateService.hasConflicts()) {
      return;
    }

    await this.facade.generateSchedule();
  }

  async onRangeChanged(range: ScheduleCalendarRange): Promise<void> {
    await this.facade.loadSchedule(range.from, range.to);
  }

  async onPlannerTaskSaved(): Promise<void> {
    await this.facade.reloadCurrentSchedule();
    this.messageService.add({
      severity: 'success',
      summary: 'Aufgabe gespeichert',
      detail: 'Der Arbeitsplan wurde aktualisiert.',
      life: 3000,
    });
  }

  async onTaskCompletionRequested(event: ScheduleTaskCompletionEvent): Promise<void> {
    try {
      await this.facade.completeTaskFromPlanner(event.request);

      event.resolve();

      this.messageService.add({
        severity: 'success',
        summary: 'Erledigt gespeichert',
        detail:
          event.request.scope === 'TASK'
            ? 'Die gesamte Aufgabe wurde vom Backend als erledigt gespeichert.'
            : 'Der Aufgabenabschnitt wurde vom Backend als erledigt gespeichert.',
        life: 5000,
      });
    } catch {
      const errorMessage =
        'Die Erledigt-Entscheidung konnte nicht gespeichert werden. Bitte prüfe, ob der Backend-Endpunkt bereits verfügbar ist.';

      event.reject(errorMessage);

      this.messageService.add({
        severity: 'error',
        summary: 'Speichern fehlgeschlagen',
        detail: errorMessage,
        life: 7000,
      });
    }
  }

  async onPlannerAppointmentSaved(): Promise<void> {
    const range = this.facade.lastRange() ?? null;

    if (range) {
      await this.facade.loadSchedule(range.from, range.to);
    }

    this.messageService.add({
      severity: 'success',
      summary: 'Kalendereintrag gespeichert',
      detail: 'Der Arbeitsplan wurde aktualisiert.',
      life: 5000,
    });
  }
}
