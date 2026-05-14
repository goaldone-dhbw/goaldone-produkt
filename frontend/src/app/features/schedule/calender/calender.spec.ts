import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BASE_PATH } from '../../../api';
import { CalenderComponent } from './calender';

describe('CalenderComponent', () => {
  let component: CalenderComponent;
  let fixture: ComponentFixture<CalenderComponent>;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(async () => {
    class ResizeObserverMock {
      observe() {}
      unobserve() {}
      disconnect() {}
    }

    globalThis.ResizeObserver = ResizeObserverMock as any;

    await TestBed.configureTestingModule({
      imports: [CalenderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CalenderComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    fixture.detectChanges();

    const accountsRequest = httpMock.expectOne(`${API_BASE}/users/accounts`);
    accountsRequest.flush({ accounts: [] });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not show chunk numbering for single-chunk tasks', () => {
    fixture.componentRef.setInput('entries', [
      {
        entryId: 'entry-1',
        type: 'TASK',
        originalItemTitle: 'Dokumentation schreiben',
        occurrenceDate: '2026-05-25',
        startTime: '10:00:00',
        endTime: '11:00:00',
        chunkIndex: 0,
        totalChunks: 1,
      },
    ]);

    fixture.detectChanges();

    const events = component.calendarEvents();
    const taskEvent = events.find((event) => event.id === 'entry-1');
    expect(taskEvent?.title).toBe('Dokumentation schreiben');
  });

  it('should show chunk numbering for tasks with multiple chunks', () => {
    fixture.componentRef.setInput('entries', [
      {
        entryId: 'entry-1',
        type: 'TASK',
        originalItemTitle: 'Dokumentation schreiben',
        occurrenceDate: '2026-05-25',
        startTime: '10:00:00',
        endTime: '11:00:00',
        chunkIndex: 0,
        totalChunks: 3,
      },
    ]);

    fixture.detectChanges();

    const events = component.calendarEvents();
    const taskEvent = events.find((event) => event.id === 'entry-1');
    expect(taskEvent?.title).toBe('Dokumentation schreiben (1/3)');
  });

  it('should add pointer cursor class for task events', () => {
    const eventClassNames = component.calendarOptions.eventClassNames as any;

    const classes = eventClassNames({
      event: {
        extendedProps: {
          entry: {
            type: 'TASK',
          },
        },
      },
    });

    expect(classes).toContain('cursor-pointer');
  });
});
