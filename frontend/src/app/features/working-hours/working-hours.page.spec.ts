import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH, DayOfWeek } from '../../api';
import { WorkingHoursPage } from './working-hours.page';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AccountStateService } from '../../core/services/account-state.service';

describe('WorkingHoursPage', () => {
  let fixture: ComponentFixture<WorkingHoursPage>;
  let component: WorkingHoursPage;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  const account = {
    accountId: 'acc-1',
    organizationName: 'Test Org',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkingHoursPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        ConfirmationService,
        MessageService,
        {
          provide: AccountStateService,
          useValue: { refresh: () => {} },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkingHoursPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(
    accounts: any[] = [],
    workingTimes: any[] = [],
    breaks: any[] = [],
  ): void {
    fixture.detectChanges();

    httpMock.expectOne(`${API_BASE}/users/accounts`).flush({ accounts });
    httpMock.expectOne(`${API_BASE}/working-times`).flush({ items: workingTimes });

    if (accounts.length > 0) {
      const breakRequests = httpMock.match((req) => req.url.includes('/appointments'));
      expect(breakRequests.length).toBe(accounts.length);

      breakRequests.forEach((req) => {
        req.flush({ items: breaks });
      });
    }

    fixture.detectChanges();
  }

  it('should create', () => {
    flushInitialRequests();

    expect(component).toBeTruthy();
  });

  it('should display empty message when no working hours exist', () => {
    flushInitialRequests();

    expect(fixture.nativeElement.textContent).toContain(
      'Es sind noch keine Arbeitszeiten hinterlegt.',
    );
  });

  it('should create one-time coffee break', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = 'Kaffeepause';
    component.breakDate = '2024-04-20';
    component.breakStartTime = '14:00';
    component.breakEndTime = '14:10';
    component.breakType = 'ONCE';
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    const request = httpMock.expectOne((req) => req.url.includes('/appointments'));

    expect(request.request.method).toBe('POST');
    expect(request.request.body.title).toBe('Kaffeepause');
    expect(request.request.body.isBreak).toBe(true);
    expect(request.request.body.appointmentType).toBe('ONE_TIME');
    expect(request.request.body.date).toBe('2024-04-20');
    expect(request.request.body.startTime).toBe('14:00');
    expect(request.request.body.endTime).toBe('14:10');

    request.flush({ id: 'break-1' });

    const reloadRequest = httpMock.expectOne((req) => req.url.includes('/appointments'));
    reloadRequest.flush({
      items: [
        {
          id: 'break-1',
          title: 'Kaffeepause',
          isBreak: true,
          appointmentType: 'ONE_TIME',
          date: '2024-04-20',
          startTime: '14:00',
          endTime: '14:10',
          accountId: 'acc-1',
        },
      ],
    });

    expect(component.breaks().length).toBe(1);
    expect(component.getBreakTitle(component.breaks()[0])).toBe('Kaffeepause');
    expect(component.getBreakDate(component.breaks()[0])).toBe('2024-04-20');
    expect(component.getBreakStart(component.breaks()[0])).toBe('14:00');
    expect(component.getBreakEnd(component.breaks()[0])).toBe('14:10');
  });

  it('should create one-time break Arzttermin', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = 'Arzttermin';
    component.breakDate = '2026-05-20';
    component.breakStartTime = '10:00';
    component.breakEndTime = '11:00';
    component.breakType = 'ONCE';
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    const request = httpMock.expectOne((req) => req.url.includes('/appointments'));

    expect(request.request.method).toBe('POST');
    expect(request.request.body.title).toBe('Arzttermin');
    expect(request.request.body.isBreak).toBe(true);
    expect(request.request.body.appointmentType).toBe('ONE_TIME');
    expect(request.request.body.date).toBe('2026-05-20');
    expect(request.request.body.startTime).toBe('10:00');
    expect(request.request.body.endTime).toBe('11:00');

    request.flush({ id: 'break-2' });

    const reloadRequest = httpMock.expectOne((req) => req.url.includes('/appointments'));
    reloadRequest.flush({
      items: [
        {
          id: 'break-2',
          title: 'Arzttermin',
          isBreak: true,
          appointmentType: 'ONE_TIME',
          date: '2026-05-20',
          startTime: '10:00',
          endTime: '11:00',
          accountId: 'acc-1',
        },
      ],
    });

    expect(component.breaks().length).toBe(1);
    expect(component.getBreakTitle(component.breaks()[0])).toBe('Arzttermin');
  });

  it('should create recurring weekday lunch break', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = 'Mittagspause';
    component.breakStartTime = '12:00';
    component.breakEndTime = '13:00';
    component.breakType = 'RECURRING';
    component.breakSelectedDays = [DayOfWeek.Monday, DayOfWeek.Wednesday, DayOfWeek.Friday];
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    const request = httpMock.expectOne((req) => req.url.includes('/appointments'));

    expect(request.request.method).toBe('POST');
    expect(request.request.body.title).toBe('Mittagspause');
    expect(request.request.body.isBreak).toBe(true);
    expect(request.request.body.appointmentType).toBe('RECURRING');
    expect(request.request.body.date).toBe(null);
    expect(request.request.body.startTime).toBe('12:00');
    expect(request.request.body.endTime).toBe('13:00');
    expect(request.request.body.rrule).toBe('FREQ=WEEKLY;BYDAY=MO,WE,FR');

    request.flush({ id: 'break-3' });

    const reloadRequest = httpMock.expectOne((req) => req.url.includes('/appointments'));
    reloadRequest.flush({
      items: [
        {
          id: 'break-3',
          title: 'Mittagspause',
          isBreak: true,
          appointmentType: 'RECURRING',
          startTime: '12:00',
          endTime: '13:00',
          accountId: 'acc-1',
        },
      ],
    });

    expect(component.breaks().length).toBe(1);
    expect(component.getBreakTitle(component.breaks()[0])).toBe('Mittagspause');
  });

  it('should not save recurring break without selected weekdays', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = 'Mittagspause';
    component.breakStartTime = '12:00';
    component.breakEndTime = '13:00';
    component.breakType = 'RECURRING';
    component.breakSelectedDays = [];
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    httpMock.expectNone((req) => req.method === 'POST' && req.url.includes('/appointments'));
  });

  it('should edit existing working time and update end time', () => {
    flushInitialRequests([account]);

    component.openEditDialog({
      id: 'wt-1',
      accountId: 'acc-1',
      organizationId: 'org-1',
      days: [DayOfWeek.Monday],
      startTime: '12:00',
      endTime: '13:00',
      createdAt: '2026-05-08T15:00:00Z',
      conflicting: false,
    });
    component.endTimeString = '13:30';
    component.saveWorkingTime();

    const request = httpMock.expectOne(`${API_BASE}/working-times/wt-1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body.days.length).toBe(1);
    expect(request.request.body.days[0]).toBe(DayOfWeek.Monday);
    expect(request.request.body.startTime).toBe('12:00');
    expect(request.request.body.endTime).toBe('13:30');

    request.flush({});

    httpMock.expectOne(`${API_BASE}/working-times`).flush({
      items: [
        {
          id: 'wt-1',
          accountId: 'acc-1',
          days: [DayOfWeek.Monday],
          startTime: '12:00',
          endTime: '13:30',
        },
      ],
    });

    expect(component.workingTimes().length).toBe(1);
    expect(component.workingTimes()[0].endTime).toBe('13:30');
  });

  it('should not save break when end time is before start time', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = 'Kaffeepause';
    component.breakDate = '2024-04-20';
    component.breakStartTime = '15:00';
    component.breakEndTime = '14:30';
    component.breakType = 'ONCE';
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    httpMock.expectNone((req) => req.method === 'POST' && req.url.includes('/appointments'));
  });

  it('should not save break when required end time is missing', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = 'Pause';
    component.breakDate = '2024-04-20';
    component.breakStartTime = '10:00';
    component.breakEndTime = '';
    component.breakType = 'ONCE';
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    httpMock.expectNone((req) => req.method === 'POST' && req.url.includes('/appointments'));
  });

  it('should not save break when required title is missing', () => {
    flushInitialRequests([account]);

    component.openCreateBreakDialog();
    component.breakTitle = '';
    component.breakDate = '2024-04-20';
    component.breakStartTime = '10:00';
    component.breakEndTime = '11:00';
    component.breakType = 'ONCE';
    component.breakSelectedAccountId = 'acc-1';

    component.saveBreak();

    httpMock.expectNone((req) => req.method === 'POST' && req.url.includes('/appointments'));
  });

  it('should not save working time when start time is after end time', () => {
    flushInitialRequests([account]);

    component.openCreateDialog();
    component.selectedAccountId = 'acc-1';
    component.selectedDays = [DayOfWeek.Monday];
    component.startTimeString = '14:00';
    component.endTimeString = '13:00';

    component.saveWorkingTime();

    httpMock.expectNone((req) => req.method === 'POST' && req.url.includes('/working-times'));
  });

  it('should create valid working time', () => {
    flushInitialRequests([account]);

    component.openCreateDialog();
    component.selectedAccountId = 'acc-1';
    component.selectedDays = [DayOfWeek.Monday, DayOfWeek.Tuesday];
    component.startTimeString = '08:00';
    component.endTimeString = '17:00';

    component.saveWorkingTime();

    const request = httpMock.expectOne(`${API_BASE}/working-times`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body.accountId).toBe('acc-1');
    expect(request.request.body.days.length).toBe(2);
    expect(request.request.body.days[0]).toBe(DayOfWeek.Monday);
    expect(request.request.body.days[1]).toBe(DayOfWeek.Tuesday);
    expect(request.request.body.startTime).toBe('08:00');
    expect(request.request.body.endTime).toBe('17:00');

    request.flush({});

    httpMock.expectOne(`${API_BASE}/working-times`).flush({
      items: [
        {
          id: 'wt-1',
          accountId: 'acc-1',
          days: [DayOfWeek.Monday, DayOfWeek.Tuesday],
          startTime: '08:00',
          endTime: '17:00',
        },
      ],
    });

    expect(component.workingTimes().length).toBe(1);
  });
});
