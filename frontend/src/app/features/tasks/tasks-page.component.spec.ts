import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH } from '../../api';
import { OrgContextService } from '../../core/services/org-context.service';
import { TasksPageComponent } from './tasks-page.component';

describe('TasksPageComponent', () => {
  let fixture: ComponentFixture<TasksPageComponent>;
  let component: TasksPageComponent;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(async () => {
    (window as any).__env = {
      apiBasePath: 'http://localhost:8080',
    };

    const orgContextServiceMock = {
      getDefaultOrg: () => ({ id: 'org-1', slug: 'test-org', role: 'USER' }),
      getDialogOrg: () => null,
      getSettingsOrg: () => null,
    };

    await TestBed.configureTestingModule({
      imports: [TasksPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        { provide: OrgContextService, useValue: orgContextServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TasksPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  async function flushInitialRequests(tasksAccountResponses: any[] = [], accountsResponse: any = { accounts: [] }): Promise<void> {
    fixture.detectChanges();

    const accountsRequest = httpMock.expectOne(`${API_BASE}/users/accounts`);
    accountsRequest.flush(accountsResponse);

    // Wait for the next microtask so loadTasks() is called
    await Promise.resolve();
    await Promise.resolve();

    const tasksRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    tasksRequest.flush(tasksAccountResponses);

    fixture.detectChanges();
  }

  function getTextContent(): string {
    return fixture.nativeElement.textContent ?? '';
  }

  it('soll eine Aufgabe erstellen und danach in der Liste anzeigen', async () => {
    const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
    await flushInitialRequests([], {
      accounts: [
        {
          accountId: accountId,
          organizationName: 'GoalDone',
        },
      ],
    });

    component.openCreateDialog();
    fixture.detectChanges();

    component.taskForm.patchValue({
      title: 'Dokumentation schreiben',
      description: 'Feature dokumentieren',
      duration: 120,
      status: 'OPEN',
      deadline: '2026-04-25T12:00',
      accountId: accountId,
      dependencyIds: [],
    });

    const savePromise = component.saveTask();

    const postRequest = httpMock.expectOne(`${API_BASE}/tasks`);

    expect(postRequest.request.body).toMatchObject({
      title: 'Dokumentation schreiben',
      description: 'Feature dokumentieren',
      duration: 120,
      status: 'OPEN',
      accountId: accountId,
      dependencyIds: [],
    });

    postRequest.flush({
      id: '00000000-0000-0000-0000-000000000001',
      title: 'Dokumentation schreiben',
      description: 'Feature dokumentieren',
      duration: 120,
      deadline: '2026-04-25T12:00:00.000Z',
      status: 'OPEN',
    });

    await Promise.resolve();
    await Promise.resolve();

    const reloadRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    reloadRequest.flush([
      {
        accountId: accountId,
        tasks: [
          {
            id: '00000000-0000-0000-0000-000000000001',
            title: 'Dokumentation schreiben',
            description: 'Feature dokumentieren',
            duration: 120,
            deadline: '2026-04-25T12:00:00.000Z',
            status: 'OPEN',
            dependencyIds: [],
          },
        ],
      },
    ]);

    await savePromise;
    fixture.detectChanges();

    expect(component.tasks().length).toBe(1);
    expect(component.tasks()[0].title).toBe('Dokumentation schreiben');
    expect(component.tasks()[0].accountLabel).toBe('GoalDone');
    expect(component.successMessage()).toContain('erfolgreich erstellt');
    expect(getTextContent()).toContain('Dokumentation schreiben');
  });

  it('soll eine Aufgabe bearbeiten und die Liste aktualisieren', async () => {
    const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
    await flushInitialRequests(
      [
        {
          accountId: accountId,
          tasks: [
            {
              id: 't1',
              title: 'Alte Aufgabe',
              description: 'Alt',
              duration: 90,
              deadline: null,
              status: 'OPEN',
              dependencyIds: [],
            },
          ],
        },
      ],
      {
        accounts: [
          {
            accountId: accountId,
            organizationName: 'GoalDone',
          },
        ],
      },
    );

    const existingTask = component.tasks()[0];
    component.openEditDialog(existingTask);
    fixture.detectChanges();

    component.taskForm.patchValue({
      title: 'Aktualisierte Aufgabe',
      duration: 150,
      status: 'IN_PROGRESS',
      description: 'Neu',
      accountId: accountId,
      dependencyIds: [],
    });

    const savePromise = component.saveTask();

    const putRequest = httpMock.expectOne(`${API_BASE}/tasks/t1`);

    expect(putRequest.request.body).toMatchObject({
      title: 'Aktualisierte Aufgabe',
      duration: 150,
      status: 'IN_PROGRESS',
      description: 'Neu',
      dependencyIds: [],
    });

    putRequest.flush({
      id: 't1',
      title: 'Aktualisierte Aufgabe',
    });

    await Promise.resolve();
    await Promise.resolve();

    const reloadRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    reloadRequest.flush([
      {
        accountId: accountId,
        tasks: [
          {
            id: 't1',
            title: 'Aktualisierte Aufgabe',
            description: 'Neu',
            duration: 150,
            deadline: null,
            status: 'IN_PROGRESS',
            dependencyIds: [],
          },
        ],
      },
    ]);

    await savePromise;
    fixture.detectChanges();

    expect(component.tasks()[0].title).toBe('Aktualisierte Aufgabe');
    expect(component.tasks()[0].duration).toBe(150);
    expect(component.successMessage()).toContain('erfolgreich aktualisiert');
    expect(getTextContent()).toContain('Aktualisierte Aufgabe');
  });

  it('soll bei fehlenden Pflichtfeldern einen Formularfehler anzeigen', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.openCreateDialog();
    fixture.detectChanges();

    component.taskForm.patchValue({
      title: '',
      duration: null,
      status: 'OPEN',
      accountId: '',
    });

    await component.saveTask();
    fixture.detectChanges();

    const postRequests = httpMock.match(`${API_BASE}/tasks`);

    expect(postRequests.length).toBe(0);
    expect(component.formErrorMessage()).toContain('Pflichtfelder');
    expect(getTextContent()).toContain('Bitte fülle alle Pflichtfelder korrekt aus.');
  });

  it('soll den Status einer Aufgabe ändern und speichern', async () => {
    const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
    await flushInitialRequests(
      [
        {
          accountId: accountId,
          tasks: [
            {
              id: 't1',
              title: 'Status-Test',
              description: null,
              duration: 60,
              deadline: null,
              status: 'OPEN',
              dependencyIds: [],
            },
          ],
        },
      ],
      {
        accounts: [
          {
            accountId: accountId,
            organizationName: 'GoalDone',
          },
        ],
      },
    );

    const task = component.tasks()[0];
    const event = {
      target: { value: 'DONE' },
    } as unknown as Event;

    const changePromise = component.changeStatus(task, event);

    const putRequest = httpMock.expectOne(`${API_BASE}/tasks/t1`);
    expect(putRequest.request.body).toMatchObject({
      status: 'DONE',
    });
    putRequest.flush({});

    await Promise.resolve();
    await Promise.resolve();

    const reloadRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    reloadRequest.flush([
      {
        accountId: accountId,
        tasks: [
          {
            id: 't1',
            title: 'Status-Test',
            description: null,
            duration: 60,
            deadline: null,
            status: 'DONE',
            dependencyIds: [],
          },
        ],
      },
    ]);

    await changePromise;
    fixture.detectChanges();

    expect(component.tasks()[0].status).toBe('DONE');
    expect(component.successMessage()).toContain('Status wurde gespeichert');
  });
});
