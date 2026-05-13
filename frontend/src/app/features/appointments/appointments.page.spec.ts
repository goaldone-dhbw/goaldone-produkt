import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AccountResponse, Appointment, AppointmentsService, UserAccountsService } from '../../api';
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
  };

  const userAccountsServiceMock = {
    getMyAccounts: vi.fn(),
  };

  beforeEach(async () => {
    appointmentsServiceMock.listAppointments.mockReset();
    appointmentsServiceMock.createAppointment.mockReset();
    userAccountsServiceMock.getMyAccounts.mockReset();

    userAccountsServiceMock.getMyAccounts.mockReturnValue(of({ accounts: [account] }));
    appointmentsServiceMock.listAppointments.mockReturnValue(
      of({ appointments: [appointment, breakAppointment] }),
    );
    appointmentsServiceMock.createAppointment.mockReturnValue(of(appointment));

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

  it('soll gespeicherte Termine laden und Pausen ausblenden', () => {
    expect(userAccountsServiceMock.getMyAccounts).toHaveBeenCalledTimes(1);
    expect(appointmentsServiceMock.listAppointments).toHaveBeenCalledWith('account-1');
    expect(component.appointments().map((item) => item.id)).toEqual(['appointment-1']);
    expect(component.appointments()[0].accountLabel).toBe('GoalDone');
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

  it('soll einen Termin als festen Blocker speichern und danach neu laden', async () => {
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

  it('soll Backend-Fehler verständlich anzeigen', async () => {
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
});
