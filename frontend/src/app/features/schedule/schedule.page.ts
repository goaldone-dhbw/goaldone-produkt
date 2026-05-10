import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { ProgressBar } from 'primeng/progressbar';

import { CalenderComponent, ScheduleCalendarRange } from './calender/calender';
import { ScheduleFacadeService } from './facade/facade';

@Component({
  selector: 'app-schedule',
  standalone: true,
  imports: [CommonModule, RouterLink, Button, ProgressBar, CalenderComponent],
  templateUrl: './schedule.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SchedulePage {
  readonly facade = inject(ScheduleFacadeService);

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
}
