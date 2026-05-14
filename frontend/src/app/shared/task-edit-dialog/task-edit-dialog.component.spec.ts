import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';

import { TaskEditDialogComponent, TaskItem } from './task-edit-dialog.component';
import { TasksService } from '../../api';
import { UserAccountsService } from '../../api';
import { of } from 'rxjs';

const mockTask: TaskItem = {
  id: 'task-1',
  title: 'Test Aufgabe',
  description: 'Eine Beschreibung',
  duration: 60,
  deadline: '2026-06-01T10:00:00Z',
  status: 'OPEN',
  accountId: 'account-1',
  accountLabel: 'Test GmbH',
  dependencyIds: [],
  cognitiveLoad: 'MODERATE',
  dontScheduleBefore: null,
  customChunkSize: null,
};

const mockAccounts = {
  accounts: [
    { accountId: 'account-1', organizationName: 'Test GmbH' },
    { accountId: 'account-2', organizationName: 'Demo AG' },
  ],
};

const mockSingleAccount = {
  accounts: [{ accountId: 'account-1', organizationName: 'Test GmbH' }],
};

describe('TaskEditDialogComponent', () => {
  let component: TaskEditDialogComponent;
  let fixture: ComponentFixture<TaskEditDialogComponent>;

  let tasksServiceSpy: Pick<TasksService, 'createTask' | 'updateTask'>;
  let userAccountsServiceSpy: Pick<UserAccountsService, 'getMyAccounts'>;

  beforeEach(async () => {
    tasksServiceSpy = {
      createTask: vi.fn().mockReturnValue(of({} as any)),
      updateTask: vi.fn().mockReturnValue(of({} as any)),
    };

    userAccountsServiceSpy = {
      getMyAccounts: vi.fn().mockReturnValue(of(mockAccounts as any)),
    };

    await TestBed.configureTestingModule({
      imports: [TaskEditDialogComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        { provide: TasksService, useValue: tasksServiceSpy },
        { provide: UserAccountsService, useValue: userAccountsServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskEditDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('Initialisierung', () => {
    it('sollte erstellt werden', () => {
      expect(component).toBeTruthy();
    });

    it('sollte Accounts beim Start laden', async () => {
      await fixture.whenStable();

      expect(userAccountsServiceSpy.getMyAccounts).toHaveBeenCalled();
      expect(component.accounts().length).toBe(2);
    });

    it('sollte bei einem Account die accountId automatisch setzen', async () => {
      vi.mocked(userAccountsServiceSpy.getMyAccounts).mockReturnValue(of(mockSingleAccount as any));

      await component.loadAccounts();

      expect(component.taskForm.get('accountId')?.value).toBe('account-1');
      expect(component.selectedAccountId()).toBe('account-1');
    });

    it('sollte hasMultipleAccounts true sein, wenn mehr als ein Account vorhanden ist', async () => {
      await fixture.whenStable();

      expect(component.hasMultipleAccounts()).toBe(true);
    });

    it('sollte hasSingleAccount false sein, wenn mehr als ein Account vorhanden ist', async () => {
      await fixture.whenStable();

      expect(component.hasSingleAccount()).toBe(false);
    });
  });

  describe('Formularvalidierung', () => {
    it('sollte ungültig sein, wenn Pflichtfelder leer sind', () => {
      component.taskForm.reset();

      expect(component.taskForm.invalid).toBe(true);
    });

    it('sollte gültig sein, wenn alle Pflichtfelder gefüllt sind', () => {
      component.taskForm.patchValue({
        title: 'Test',
        durationHours: 0,
        durationMinutes: 30,
        status: 'OPEN',
        accountId: 'account-1',
      });

      expect(component.taskForm.valid).toBe(true);
    });

    it('sollte Fehler zeigen, wenn Titel leer und touched ist', () => {
      component.taskForm.patchValue({ title: '' });
      component.taskForm.get('title')?.markAsTouched();

      expect(component.showFieldError('title')).toBe(true);
    });

    it('sollte keinen Fehler zeigen, wenn Titel gefüllt ist', () => {
      component.taskForm.patchValue({ title: 'Test' });
      component.taskForm.get('title')?.markAsTouched();

      expect(component.showFieldError('title')).toBe(false);
    });

    it('sollte Fehler setzen, wenn Gesamtdauer 0 ist', () => {
      component.taskForm.patchValue({
        accountId: "123",
        id: "123",
        title: "Unknown",
        durationHours: 0,
        durationMinutes: 0,
        status: "OPEN"
      });

      component.save();

      expect(component.formErrorMessage()).toBe(
        'Bitte gib eine Dauer von mindestens einer Minute an.',
      );
    });

    it('sollte dontScheduleBeforeAfterDeadline Fehler setzen, wenn "Nicht planen vor" nach der Deadline liegt', () => {
      component.taskForm.patchValue({
        deadline: '2026-05-01T10:00',
        dontScheduleBefore: '2026-06-01T10:00',
      });

      expect(component.taskForm.errors?.['dontScheduleBeforeAfterDeadline']).toBe(true);
    });

    it('sollte keinen Datumsfehler setzen, wenn die Reihenfolge korrekt ist', () => {
      component.taskForm.patchValue({
        deadline: '2026-06-01T10:00',
        dontScheduleBefore: '2026-05-01T10:00',
      });

      expect(component.taskForm.errors?.['dontScheduleBeforeAfterDeadline']).toBeFalsy();
    });

    it('sollte chunkSizeInvalid Fehler setzen, wenn Chunk-Größe größer als Dauer ist', () => {
      component.taskForm.patchValue({
        durationHours: 1,
        durationMinutes: 0,
        customChunkSize: 90,
      });

      expect(component.taskForm.errors?.['chunkSizeInvalid']).toBe(true);
    });

    it('sollte keinen chunkSize Fehler setzen, wenn Chunk-Größe kleiner als Dauer ist', () => {
      component.taskForm.patchValue({
        durationHours: 1,
        durationMinutes: 0,
        customChunkSize: 30,
      });

      expect(component.taskForm.errors?.['chunkSizeInvalid']).toBeFalsy();
    });
  });

  describe('Abhängigkeiten', () => {
    beforeEach(() => {
      component.selectedAccountId.set('account-1');
    });

    it('sollte isDependencySelected false zurückgeben, wenn keine Abhängigkeit gewählt ist', () => {
      component.taskForm.patchValue({
        dependencyIds: [],
      });

      expect(component.isDependencySelected('task-2')).toBe(false);
    });

    it('sollte isDependencySelected true zurückgeben, wenn die Abhängigkeit gewählt ist', () => {
      component.taskForm.patchValue({
        dependencyIds: ['task-2'],
      });

      expect(component.isDependencySelected('task-2')).toBe(true);
    });

    it('sollte eine Abhängigkeit auswählen, wenn sie noch nicht ausgewählt ist', () => {
      const event = new MouseEvent('click');
      vi.spyOn(event, 'preventDefault');

      component.taskForm.patchValue({
        dependencyIds: [],
      });

      component.selectDependency('task-2', event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(component.taskForm.getRawValue().dependencyIds).toEqual(['task-2']);
      expect(component.isDependencySelected('task-2')).toBe(true);
    });

    it('sollte eine Abhängigkeit abwählen, wenn dieselbe ID nochmals ausgewählt wird', () => {
      const event = new MouseEvent('click');
      vi.spyOn(event, 'preventDefault');

      component.taskForm.patchValue({
        dependencyIds: ['task-2'],
      });

      component.selectDependency('task-2', event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(component.taskForm.getRawValue().dependencyIds).toEqual([]);
      expect(component.isDependencySelected('task-2')).toBe(false);
    });

    it('sollte die bisherige Abhängigkeit ersetzen, wenn eine andere Abhängigkeit ausgewählt wird', () => {
      const event = new MouseEvent('click');
      vi.spyOn(event, 'preventDefault');

      component.taskForm.patchValue({
        dependencyIds: ['task-2'],
      });

      component.selectDependency('task-3', event);

      expect(component.taskForm.getRawValue().dependencyIds).toEqual(['task-3']);
      expect(component.isDependencySelected('task-2')).toBe(false);
      expect(component.isDependencySelected('task-3')).toBe(true);
    });

    it('sollte den aktuellen Task aus den Abhängigkeitsoptionen ausschließen', () => {
      component.currentTaskId.set('task-1');
      component.selectedAccountId.set('account-1');

      fixture.componentRef.setInput('allTasks', [
        mockTask,
        {
          ...mockTask,
          id: 'task-2',
          title: 'Andere Aufgabe',
          accountId: 'account-1',
        },
      ]);

      fixture.detectChanges();

      const options = component.availableDependencyOptions();

      expect(options.find((option) => option.id === 'task-1')).toBeUndefined();
      expect(options.find((option) => option.id === 'task-2')).toBeTruthy();
    });

    it('sollte nur Tasks desselben Accounts anzeigen', () => {
      component.currentTaskId.set(undefined);
      component.selectedAccountId.set('account-1');

      fixture.componentRef.setInput('allTasks', [
        {
          ...mockTask,
          id: 'task-2',
          title: 'Account 1 Aufgabe',
          accountId: 'account-1',
        },
        {
          ...mockTask,
          id: 'task-3',
          title: 'Account 2 Aufgabe',
          accountId: 'account-2',
        },
      ]);

      fixture.detectChanges();

      const options = component.availableDependencyOptions();

      expect(options.find((option) => option.id === 'task-2')).toBeTruthy();
      expect(options.find((option) => option.id === 'task-3')).toBeUndefined();
    });
  });

  describe('Speichern', () => {
    beforeEach(() => {
      component.taskForm.patchValue({
        title: 'Test Aufgabe',
        durationHours: 1,
        durationMinutes: 0,
        status: 'OPEN',
        accountId: 'account-1',
      });
    });

    it('sollte createTask aufrufen, wenn kein Task gesetzt ist', async () => {
      component.task = null;

      component.save();
      await fixture.whenStable();

      expect(tasksServiceSpy.createTask).toHaveBeenCalled();
    });

    it('sollte updateTask aufrufen, wenn ein Task gesetzt ist', async () => {
      component.task = mockTask;

      component.save();
      await fixture.whenStable();

      expect(tasksServiceSpy.updateTask).toHaveBeenCalledWith('task-1', expect.any(Object));
    });

    it('sollte beim Erstellen die ausgewählten dependencyIds mitsenden', async () => {
      component.task = null;

      component.taskForm.patchValue({
        title: 'Test Aufgabe',
        durationHours: 1,
        durationMinutes: 0,
        status: 'OPEN',
        accountId: 'account-1',
        dependencyIds: ['task-2'],
      });

      component.save();
      await fixture.whenStable();

      expect(tasksServiceSpy.createTask).toHaveBeenCalledWith(
        expect.objectContaining({
          dependencyIds: ['task-2'],
        }),
      );
    });

    it('sollte beim Aktualisieren leere dependencyIds mitsenden, wenn die Abhängigkeit abgewählt wurde', async () => {
      component.task = mockTask;

      component.taskForm.patchValue({
        title: 'Test Aufgabe',
        durationHours: 1,
        durationMinutes: 0,
        status: 'OPEN',
        accountId: 'account-1',
        dependencyIds: [],
      });

      component.save();
      await fixture.whenStable();

      expect(tasksServiceSpy.updateTask).toHaveBeenCalledWith(
        'task-1',
        expect.objectContaining({
          dependencyIds: [],
        }),
      );
    });

    it('sollte Fehlermeldung anzeigen, wenn Formular ungültig ist', () => {
      component.taskForm.reset();

      component.save();

      expect(component.formErrorMessage()).toBe('Bitte fülle alle Pflichtfelder korrekt aus.');
    });

    it('sollte taskSaved emittieren nach erfolgreichem Speichern', async () => {
      vi.spyOn(component.taskSaved, 'emit');
      component.task = null;

      component.save();
      await fixture.whenStable();

      expect(component.taskSaved.emit).toHaveBeenCalled();
    });

    it('sollte isOpenChange mit false emittieren nach erfolgreichem Speichern', async () => {
      vi.spyOn(component.isOpenChange, 'emit');
      component.task = null;

      component.save();
      await fixture.whenStable();

      expect(component.isOpenChange.emit).toHaveBeenCalledWith(false);
    });
  });

  describe('Formatierungen', () => {
    it('sollte Status korrekt formatieren', () => {
      expect(component.formatStatus('OPEN')).toBe('Offen');
      expect(component.formatStatus('IN_PROGRESS')).toBe('In Bearbeitung');
      expect(component.formatStatus('DONE')).toBe('Erledigt');
    });

    it('sollte CognitiveLoad korrekt formatieren', () => {
      expect(component.formatCognitiveLoad('LOW')).toBe('Niedrig');
      expect(component.formatCognitiveLoad('MODERATE')).toBe('Mittel');
      expect(component.formatCognitiveLoad('HIGH')).toBe('Hoch');
    });
  });

  describe('Abbrechen', () => {
    it('sollte isOpenChange mit false emittieren', () => {
      vi.spyOn(component.isOpenChange, 'emit');

      component.cancel();

      expect(component.isOpenChange.emit).toHaveBeenCalledWith(false);
    });
  });
});
