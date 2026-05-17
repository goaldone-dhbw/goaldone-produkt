import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  MultiAccountScheduleResponse,
  ScheduleEntry,
  ScheduleResponse,
  SchedulesService,
} from '../../../api';

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

export type ScheduleTaskCompletionUpdatedEntriesResponse = {
  updatedEntries?: ScheduleEntry[] | null;
  entries?: ScheduleEntry[] | null;
};

export type ScheduleTaskCompletionResponse =
  | ScheduleEntry[]
  | ScheduleTaskCompletionUpdatedEntriesResponse
  | ScheduleResponse
  | MultiAccountScheduleResponse;

@Injectable({
  providedIn: 'root',
})
export class ScheduleCompletionApiService {
  private readonly http = inject(HttpClient);
  private readonly schedulesService = inject(SchedulesService);

  /**
   * Der Endpoint wird bewusst direkt aufgerufen, bis er in OpenAPI ergänzt wurde.
   * Erwarteter Backend-Request:
   * {
   *   scope: 'CHUNK' | 'TASK',
   *   scheduleEntryId: 'UUID'
   * }
   */
  completeTaskFromPlanner(
    request: ScheduleTaskCompletionRequest,
  ): Observable<ScheduleTaskCompletionResponse> {
    const basePath = this.schedulesService.configuration.basePath || 'http://localhost:8080/api/v1';

    return this.http.post<ScheduleTaskCompletionResponse>(
      `${basePath}/schedules/task-completions`,
      request,
      {
        headers: this.createHeaders(),
      },
    );
  }

  private createHeaders(): HttpHeaders {
    let headers = new HttpHeaders()
      .set('Accept', 'application/json')
      .set('Content-Type', 'application/json');

    const token = this.schedulesService.configuration.lookupCredential('bearerAuth');

    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    return headers;
  }
}
