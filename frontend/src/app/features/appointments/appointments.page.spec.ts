import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  AccountResponse,
  Appointment,
  AppointmentsService,
  DayOfWeek,
  UserAccountsService,
} from '../../api';
import { AppointmentsPage } from './appointments.page';

describe('AppointmentsPage', () => {
  let component: AppointmentsPage;
  let fixture: ComponentFixture<AppointmentsPage>;

  const account: AccountResponse = {
    accountId: 'account-1',
    organizationId: 'org-1',
    organizationName: 'GoalDone',
    roles: [],
    hasConflicts: false,
    active: true,
  };

  const appointment: Appointment = {
    id: 'appointment-1',
    accountId: 'account-1',
    title: 'Arzttermin',
    isBreak: false,
    appointmentType: 'ONE_TIME',
    date: '2026-05-12',
    startTime: '10:00',
    endTime: '11:00',
    rrule: null,
    createdAt: '2026-05-01T08:00:00',
  };

  const recurringAppointment: Appointment = {
    id: 'appointment-2',
    accountId: 'account-1',
    title: 'Daily Standup',
    isBreak: false,
    appointmentType: 'RECURRING',
    date: '2026-05-12',
    startTime: '09:00',
    endTime: '09:30',
    rrule: 'FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20260630',
    createdAt: '2026-05-01T08:00:00',
  };

  const breakAppointment: Appointment = {
    id: 'break-1',
    accountId: 'account-1',
    title: 'Mittagspause',
    isBreak: true,
    appointmentType: 'ONE_TIME',
    date: '2026-05-12',
    startTime: '12:00',
    endTime: '13:00',
    rrule: null,
    createdAt: '2026-05-01T08:00:00',
  };

  const appointmentsServiceMock = {
    listAppointments: vi.fn(),
    createAppointment: vi.fn(),
    updateAppointment: vi.fn(),
    deleteAppointment: vi.fn(),
  };

  const userAccountsServiceMock = {
    getMyAccounts: vi.fn(),
  };

  beforeEach(async () => {
    appointmentsServiceMock.listAppointments.mockReset();
    appointmentsServiceMock.createAppointment.mockReset();
    appointmentsServiceMock.updateAppointment.mockReset();
    appointmentsServiceMock.deleteAppointment.mockReset();
    userAccountsServiceMock.getMyAccounts.mockReset();

    userAccountsServiceMock.getMyAccounts.mockReturnValue(of({ accounts: [account] }));
    appointmentsServiceMock.listAppointments.mockReturnValue(
      of({ appointments: [appointment, breakAppointment] }),
    );
    appointmentsServiceMock.createAppointment.mockReturnValue(of(appointment));
    appointmentsServiceMock.updateAppointment.mockReturnValue(of(appointment));
    appointmentsServiceMock.deleteAppointment.mockReturnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [AppointmentsPage],
      providers: [
        { provide: AppointmentsService, useValue: appointmentsServiceMock },
        { provide: UserAccountsService, useValue: userAccountsServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppointmentsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();

    await Promise.resolve();
    await Promise.resolve();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('soll gespeicherte Termine laden und Pausen ausblenden', () => {
    expect(userAccountsServiceMock.getMyAccounts).toHaveBeenCalledTimes(1);
    expect(appointmentsServiceMock.listAppointments).toHaveBeenCalledWith('account-1');
    expect(component.appointments().map((item) => item.id)).toEqual(['appointment-1']);
    expect(component.appointments()[0].accountLabel).toBe('GoalDone');
    expect(component.appointments()[0].accountId).toBe('account-1');
  });

  it('soll Ladefehler verständlich anzeigen', async () => {
    appointmentsServiceMock.listAppointments.mockReturnValue(
      throwError(() => ({ error: { detail: 'Termine konnten nicht gelesen werden.' } })),
    );

    await component.loadAppointments();

    expect(component.appointments()).toEqual([]);
    expect(component.errorMessage()).toBe('Termine konnten nicht gelesen werden.');
  });

  it('soll Pflichtfelder validieren und ohne gültige Angaben nicht speichern', async () => {
    component.openCreateDialog();

    await component.saveAppointment();

    expect(component.validationMessage()).toBe('Bitte gib einen Titel ein.');
    expect(appointmentsServiceMock.createAppointment).not.toHaveBeenCalled();
  });

  it('soll die Endzeit nach der Startzeit validieren', async () => {
    component.openCreateDialog();
    component.title = 'Kundentermin';
    component.date = '2026-05-12';
    component.startTime = '11:00';
    component.endTime = '10:00';

    await component.saveAppointment();

    expect(component.validationMessage()).toBe('Die Endzeit muss nach der Startzeit liegen.');
    expect(appointmentsServiceMock.createAppointment).not.toHaveBeenCalled();
  });

  it('soll einen einmaligen Termin als festen Blocker speichern und danach neu laden', async () => {
    component.openCreateDialog();
    component.title = 'Kundentermin';
    component.date = '2026-05-12';
    component.startTime = '10:00';
    component.endTime = '11:00';

    await component.saveAppointment();

    expect(appointmentsServiceMock.createAppointment).toHaveBeenCalledWith('account-1', {
      title: 'Kundentermin',
      isBreak: false,
      appointmentType: 'ONE_TIME',
      date: '2026-05-12',
      startTime: '10:00',
      endTime: '11:00',
      rrule: null,
    });

    expect(component.successMessage()).toBe('Der Termin wurde gespeichert.');
    expect(component.isDialogOpen()).toBe(false);
    expect(appointmentsServiceMock.listAppointments).toHaveBeenCalledTimes(2);
  });

  it('soll einen wiederkehrenden Termin mit Start- und Enddatum speichern', async () => {
    component.openCreateDialog();
    component.title = 'Daily Standup';
    component.appointmentType = 'RECURRING';
    component.date = '2026-05-12';
    component.recurrenceEndDate = '2026-06-30';
    component.selectedDays = [DayOfWeek.Tuesday, DayOfWeek.Thursday];
    component.startTime = '09:00';
    component.endTime = '09:30';

    await component.saveAppointment();

    expect(appointmentsServiceMock.createAppointment).toHaveBeenCalledWith('account-1', {
      title: 'Daily Standup',
      isBreak: false,
      appointmentType: 'RECURRING',
      date: '2026-05-12',
      startTime: '09:00',
      endTime: '09:30',
      rrule: 'FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20260630',
    });

    expect(component.successMessage()).toBe('Der Termin wurde gespeichert.');
    expect(component.isDialogOpen()).toBe(false);
  });

  it('soll einen wiederkehrenden Termin ohne Startdatum nicht speichern', async () => {
    component.openCreateDialog();
    component.title = 'Daily Standup';
    component.appointmentType = 'RECURRING';
    component.recurrenceEndDate = '2026-06-30';
    component.selectedDays = [DayOfWeek.Tuesday];
    component.startTime = '09:00';
    component.endTime = '09:30';

    await component.saveAppointment();

    expect(component.validationMessage()).toBe('Bitte gib ein Startdatum ein.');
    expect(appointmentsServiceMock.createAppointment).not.toHaveBeenCalled();
  });

  it('soll einen wiederkehrenden Termin ohne Enddatum nicht speichern', async () => {
    component.openCreateDialog();
    component.title = 'Daily Standup';
    component.appointmentType = 'RECURRING';
    component.date = '2026-05-12';
    component.selectedDays = [DayOfWeek.Tuesday];
    component.startTime = '09:00';
    component.endTime = '09:30';

    await component.saveAppointment();

    expect(component.validationMessage()).toBe('Bitte gib ein Enddatum ein.');
    expect(appointmentsServiceMock.createAppointment).not.toHaveBeenCalled();
  });

  it('soll ein Enddatum vor dem Startdatum bei wiederkehrenden Terminen ablehnen', async () => {
    component.openCreateDialog();
    component.title = 'Daily Standup';
    component.appointmentType = 'RECURRING';
    component.date = '2026-06-30';
    component.recurrenceEndDate = '2026-05-12';
    component.selectedDays = [DayOfWeek.Tuesday];
    component.startTime = '09:00';
    component.endTime = '09:30';

    await component.saveAppointment();

    expect(component.validationMessage()).toBe('Das Enddatum darf nicht vor dem Startdatum liegen.');
    expect(appointmentsServiceMock.createAppointment).not.toHaveBeenCalled();
  });

  it('soll einen wiederkehrenden Termin ohne Wochentag nicht speichern', async () => {
    component.openCreateDialog();
    component.title = 'Daily Standup';
    component.appointmentType = 'RECURRING';
    component.date = '2026-05-12';
    component.recurrenceEndDate = '2026-06-30';
    component.selectedDays = [];
    component.startTime = '09:00';
    component.endTime = '09:30';

    await component.saveAppointment();

    expect(component.validationMessage()).toBe('Bitte wähle mindestens einen Wochentag aus.');
    expect(appointmentsServiceMock.createAppointment).not.toHaveBeenCalled();
  });

  it('soll einen bestehenden einmaligen Termin bearbeiten', async () => {
    component.openEditDialog(component.appointments()[0]);

    component.title = 'Geänderter Termin';
    component.date = '2026-05-13';
    component.startTime = '14:00';
    component.endTime = '15:00';

    await component.saveAppointment();

    expect(appointmentsServiceMock.updateAppointment).toHaveBeenCalledWith(
      'account-1',
      'appointment-1',
      {
        title: 'Geänderter Termin',
        isBreak: false,
        appointmentType: 'ONE_TIME',
        date: '2026-05-13',
        startTime: '14:00',
        endTime: '15:00',
        rrule: null,
      },
    );

    expect(component.successMessage()).toBe('Der Termin wurde aktualisiert.');
    expect(component.isDialogOpen()).toBe(false);
  });

  it('soll einen bestehenden wiederkehrenden Termin zum Bearbeiten in das Formular übernehmen', async () => {
    appointmentsServiceMock.listAppointments.mockReturnValue(
      of({ appointments: [recurringAppointment] }),
    );

    await component.loadAppointments();

    component.openEditDialog(component.appointments()[0]);

    expect(component.title).toBe('Daily Standup');
    expect(component.appointmentType).toBe('RECURRING');
    expect(component.date).toBe('2026-05-12');
    expect(component.recurrenceEndDate).toBe('2026-06-30');
    expect(component.selectedDays).toEqual([DayOfWeek.Tuesday, DayOfWeek.Thursday]);
    expect(component.startTime).toBe('09:00');
    expect(component.endTime).toBe('09:30');
  });

  it('soll einen bestehenden wiederkehrenden Termin bearbeiten', async () => {
    appointmentsServiceMock.listAppointments.mockReturnValue(
      of({ appointments: [recurringAppointment] }),
    );

    await component.loadAppointments();

    component.openEditDialog(component.appointments()[0]);

    component.title = 'Team Sync';
    component.date = '2026-05-13';
    component.recurrenceEndDate = '2026-07-31';
    component.selectedDays = [DayOfWeek.Monday, DayOfWeek.Wednesday];
    component.startTime = '10:00';
    component.endTime = '10:30';

    await component.saveAppointment();

    expect(appointmentsServiceMock.updateAppointment).toHaveBeenCalledWith(
      'account-1',
      'appointment-2',
      {
        title: 'Team Sync',
        isBreak: false,
        appointmentType: 'RECURRING',
        date: '2026-05-13',
        startTime: '10:00',
        endTime: '10:30',
        rrule: 'FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260731',
      },
    );

    expect(component.successMessage()).toBe('Der Termin wurde aktualisiert.');
  });

  it('soll einen Termin löschen', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    await component.deleteAppointment(component.appointments()[0]);

    expect(appointmentsServiceMock.deleteAppointment).toHaveBeenCalledWith(
      'account-1',
      'appointment-1',
    );

    expect(component.successMessage()).toBe('Der Termin wurde gelöscht.');
    expect(appointmentsServiceMock.listAppointments).toHaveBeenCalledTimes(2);
  });

  it('soll einen Termin nicht löschen, wenn die Bestätigung abgebrochen wird', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    await component.deleteAppointment(component.appointments()[0]);

    expect(appointmentsServiceMock.deleteAppointment).not.toHaveBeenCalled();
  });

  it('soll Backend-Fehler beim Speichern verständlich anzeigen', async () => {
    appointmentsServiceMock.createAppointment.mockReturnValue(
      throwError(() => ({ error: { detail: 'Termin überschneidet sich.' } })),
    );

    component.openCreateDialog();
    component.title = 'Kundentermin';
    component.date = '2026-05-12';
    component.startTime = '10:00';
    component.endTime = '11:00';

    await component.saveAppointment();

    expect(component.errorMessage()).toBe('Termin überschneidet sich.');
    expect(component.isDialogOpen()).toBe(true);
  });

  it('soll Backend-Fehler beim Löschen verständlich anzeigen', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    appointmentsServiceMock.deleteAppointment.mockReturnValue(
      throwError(() => ({ error: { detail: 'Termin konnte nicht gelöscht werden.' } })),
    );

    await component.deleteAppointment(component.appointments()[0]);

    expect(component.errorMessage()).toBe('Termin konnte nicht gelöscht werden.');
  });
});
