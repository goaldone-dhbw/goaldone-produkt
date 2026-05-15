import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { ProgressBar } from 'primeng/progressbar';

import { CalenderComponent, ScheduleCalendarRange } from './calender/calender';
import { ScheduleFacadeService } from './facade/facade';
import { Toast } from 'primeng/toast';
import { MessageService } from 'primeng/api';

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
  private readonly messageService = inject(MessageService);

  constructor() {
    void this.facade.initialize();
  }

  async onAccountChanged(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    await this.facade.selectAccount(select.value);
  }

  async onGenerateSchedule(): Promise<void> {
    await this.facade.generateSchedule();
  }

  async onRangeChanged(range: ScheduleCalendarRange): Promise<void> {
    await this.facade.loadSchedule(range.from, range.to);
  }

  onPlannerTaskSaved(): void {
    this.messageService.add({
      severity: 'info',
      summary: 'Aufgabe gespeichert',
      detail:
        'Die Änderungen werden im Arbeitsplan erst berücksichtigt, wenn du die Planung erneut startest.',
      life: 5000,
    });
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
