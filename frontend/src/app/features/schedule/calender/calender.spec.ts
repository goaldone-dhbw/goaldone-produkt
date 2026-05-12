import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalenderComponent } from './calender';

describe('CalenderComponent', () => {
  let component: CalenderComponent;
  let fixture: ComponentFixture<CalenderComponent>;

  beforeEach(async () => {
    class ResizeObserverMock {
      observe() {}
      unobserve() {}
      disconnect() {}
    }

    globalThis.ResizeObserver = ResizeObserverMock as any;

    await TestBed.configureTestingModule({
      imports: [CalenderComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CalenderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
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

    expect(events.length).toBe(1);
    expect(events[0].title).toBe('Dokumentation schreiben');
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

    expect(events.length).toBe(1);
    expect(events[0].title).toBe('Dokumentation schreiben (1/3)');
  });
});
