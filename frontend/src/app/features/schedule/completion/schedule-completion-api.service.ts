import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { MarkScheduleEntryRequest, MarkScheduleEntryResponse, SchedulesService } from '../../../api';

export type ScheduleCompletionScope = 'CHUNK' | 'TASK';

export type ScheduleTaskCompletionRequest = {
  /**
   * CHUNK = nur der konkrete ScheduleEntry.
   * TASK = komplette Aufgabe, abgeleitet über den ScheduleEntry im Backend.
   */
  scope: ScheduleCompletionScope;

  /**
   * Muss vom Backend pro ScheduleEntry geliefert werden.
   * Ohne diese ID wird keine Erledigt-Entscheidung ans Backend geschickt.
   */
  scheduleEntryId: string;
};

export type ScheduleTaskCompletionEvent = {
  request: ScheduleTaskCompletionRequest;
  resolve: () => void;
  reject: (message?: string) => void;
};

@Injectable({
  providedIn: 'root',
})
export class ScheduleCompletionApiService {
  private readonly schedulesService = inject(SchedulesService);

  /**
   * Marks a schedule entry as done via PATCH /schedules/{accountId}/entries/{entryId}.
   */
  completeTaskFromPlanner(
    request: ScheduleTaskCompletionRequest,
    accountId: string,
  ): Observable<MarkScheduleEntryResponse> {
    const body: MarkScheduleEntryRequest = { scope: request.scope };

    return this.schedulesService.markScheduleEntryDone(accountId, request.scheduleEntryId, body);
  }
}
