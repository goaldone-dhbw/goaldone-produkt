import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountListResponse, BASE_PATH, TaskAccountListResponse, TaskResponse } from '../../api';
import { TasksPageComponent } from './tasks-page.component';

type RuntimeWindow = Window & {
  __env?: {
    apiBasePath: string;
  };
};

describe('TasksPageComponent', () => {
  let fixture: ComponentFixture<TasksPageComponent>;
  let component: TasksPageComponent;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';
  const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
  const filterTestTasks: TaskResponse[] = [
    {
      id: 't-open-low',
      title: 'Offene niedrige Aufgabe',
      duration: 30,
      deadline: '2026-05-10T09:00:00',
      status: 'OPEN',
      cognitiveLoad: 'LOW',
      dependencyIds: [],
    },
    {
      id: 't-progress-high',
      title: 'Laufende schwere Aufgabe',
      duration: 60,
      deadline: '2026-05-15T12:00:00',
      status: 'IN_PROGRESS',
      cognitiveLoad: 'HIGH',
      dependencyIds: [],
    },
    {
      id: 't-done-moderate',
      title: 'Erledigte moderate Aufgabe',
      duration: 90,
      deadline: '2026-05-20T18:00:00',
      status: 'DONE',
      cognitiveLoad: 'MODERATE',
      dependencyIds: [],
    },
  ];

  beforeEach(async () => {
    (window as RuntimeWindow).__env = {
      apiBasePath: 'http://localhost:8080',
    };

    await TestBed.configureTestingModule({
      imports: [TasksPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TasksPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  async function flushInitialRequests(
    tasksAccountResponses: TaskAccountListResponse[] = [],
    accountsResponse: AccountListResponse = { accounts: [] },
  ): Promise<void> {
    fixture.detectChanges();

    // Match all accounts requests (TasksPageComponent + TaskEditDialogComponent)
    const accountsRequests = httpMock.match(`${API_BASE}/users/accounts`);
    accountsRequests.forEach((req) => req.flush(accountsResponse));

    // Wait for the next microtask so loadTasks() is called
    await Promise.resolve();
    await Promise.resolve();

    const tasksRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    tasksRequest.flush(tasksAccountResponses);

    fixture.detectChanges();
  }

  async function reloadTasksWithResponse(tasks: TaskResponse[]): Promise<void> {
    const loadPromise = component.loadTasks();

    const tasksRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    tasksRequest.flush([
      {
        accountId,
        tasks,
      },
    ]);

    await loadPromise;
    fixture.detectChanges();
  }

  it('soll Tasks laden und anzeigen', async () => {
    await flushInitialRequests(
      [
        {
          accountId,
          tasks: [
            {
              id: 't1',
              title: 'Test Aufgabe',
              description: 'Test Description',
              duration: 90,
              status: 'OPEN',
              dependencyIds: [],
            },
          ],
        },
      ],
      {
        accounts: [
          {
            accountId,
            organizationName: 'GoalDone',
            organizationId: 'org-1',
            roles: [],
            hasConflicts: false,
          },
        ],
      },
    );

    expect(component.tasks().length).toBe(1);
    expect(component.tasks()[0].title).toBe('Test Aufgabe');
    expect(component.tasks()[0].accountLabel).toBe('GoalDone');
  });

  it('soll Create Dialog öffnen', async () => {
    await flushInitialRequests([], { accounts: [] });

    expect(component.isTaskPopupOpen()).toBe(false);
    expect(component.editingTask()).toBeNull();

    component.openCreateDialog();

    expect(component.isTaskPopupOpen()).toBe(true);
    expect(component.editingTask()).toBeNull();
  });

  it('soll Edit Dialog öffnen mit Aufgabe', async () => {
    const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
    await flushInitialRequests(
      [
        {
          accountId,
          tasks: [
            {
              id: 't1',
              title: 'Test Aufgabe',
              description: 'Test',
              duration: 90,
              status: 'OPEN',
              dependencyIds: [],
            },
          ],
        },
      ],
      { accounts: [] },
    );

    const task = component.tasks()[0];
    component.openEditDialog(task);

    expect(component.isTaskPopupOpen()).toBe(true);
    expect(component.editingTask()).toBeTruthy();
    expect(component.editingTask()?.id).toBe('t1');
  });

  it('soll den Status einer Aufgabe ändern', async () => {
    await flushInitialRequests(
      [
        {
          accountId,
          tasks: [
            {
              id: 't1',
              title: 'Status-Test',
              duration: 60,
              status: 'OPEN',
              dependencyIds: [],
            },
          ],
        },
      ],
      { accounts: [] },
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
        accountId,
        tasks: [
          {
            id: 't1',
            title: 'Status-Test',
            duration: 60,
            status: 'DONE',
            dependencyIds: [],
          },
        ],
      },
    ]);

    await changePromise;
    fixture.detectChanges();
    expect(component.tasks()[0].status).toBe('DONE');
  });

  it('soll Aufgaben nach Status filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'IN_PROGRESS';
    await reloadTasksWithResponse(filterTestTasks);

    expect(component.tasks().map((task) => task.id)).toEqual(['t-progress-high']);
  });

  it('soll eine passende Meldung anzeigen, wenn keine Aufgaben vorhanden sind', async () => {
    await flushInitialRequests([], { accounts: [] });

    expect(component.tasks()).toEqual([]);
    expect(component.totalTaskCount()).toBe(0);
    expect(component.getEmptyTasksMessage()).toBe('Es sind noch keine Aufgaben vorhanden.');
  });

  it('soll eine passende Meldung anzeigen, wenn keine Aufgaben zum Filter passen', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'DONE';
    component.filters.difficulty = 'HIGH';
    await reloadTasksWithResponse(filterTestTasks);

    expect(component.tasks()).toEqual([]);
    expect(component.totalTaskCount()).toBe(3);
    expect(component.getEmptyTasksMessage()).toBe(
      'Zu diesem Filter sind keine Aufgaben vorhanden.',
    );
  });

  it('soll Aufgaben nach Anforderungsgrad filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.difficulty = 'HIGH';
    await reloadTasksWithResponse(filterTestTasks);

    expect(component.tasks().map((task) => task.id)).toEqual(['t-progress-high']);
  });

  it('soll Aufgaben nach Dauer filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.duration = 90;
    await reloadTasksWithResponse(filterTestTasks);

    expect(component.tasks().map((task) => task.id)).toEqual(['t-done-moderate']);
  });

  it('soll Aufgaben nach Deadline-Zeitraum filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.deadlineFrom = new Date('2026-05-12T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-18T23:59:59');
    await reloadTasksWithResponse(filterTestTasks);

    expect(component.tasks().map((task) => task.id)).toEqual(['t-progress-high']);
  });

  it('soll Aufgaben ohne Deadline ausfiltern, wenn ein Deadline-Zeitraum gesetzt ist', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.deadlineFrom = new Date('2026-05-01T00:00:00');
    await reloadTasksWithResponse([
      ...filterTestTasks,
      {
        id: 't-without-deadline',
        title: 'Aufgabe ohne Deadline',
        duration: 30,
        status: 'OPEN',
        cognitiveLoad: 'LOW',
        dependencyIds: [],
      },
    ]);

    expect(component.tasks().map((task) => task.id)).toEqual([
      't-open-low',
      't-progress-high',
      't-done-moderate',
    ]);
  });

  it('soll mehrere Filter kombinieren', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'DONE';
    component.filters.difficulty = 'MODERATE';
    component.filters.duration = 90;
    component.filters.deadlineFrom = new Date('2026-05-19T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-21T23:59:59');
    await reloadTasksWithResponse(filterTestTasks);

    expect(component.tasks().map((task) => task.id)).toEqual(['t-done-moderate']);
  });

  it('soll Filter zurücksetzen und danach alle Aufgaben anzeigen', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'DONE';
    component.filters.difficulty = 'MODERATE';
    component.filters.duration = 90;
    component.filters.deadlineFrom = new Date('2026-05-19T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-21T23:59:59');
    component.dateRange = [component.filters.deadlineFrom, component.filters.deadlineTo];

    component.resetFilters();
    const tasksRequest = httpMock.expectOne(`${API_BASE}/tasks/all`);
    tasksRequest.flush([
      {
        accountId,
        tasks: filterTestTasks,
      },
    ]);

    await Promise.resolve();
    fixture.detectChanges();

    expect(component.filters).toEqual({
      status: null,
      difficulty: null,
      deadlineFrom: null,
      deadlineTo: null,
      duration: null,
    });
    expect(component.dateRange).toEqual([]);
    expect(component.tasks().map((task) => task.id)).toEqual([
      't-open-low',
      't-progress-high',
      't-done-moderate',
    ]);
  });
});
