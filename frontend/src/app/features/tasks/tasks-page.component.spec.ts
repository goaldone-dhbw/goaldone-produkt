import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AccountListResponse, BASE_PATH, TaskResponse } from '../../api';
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
  let router: Router;
  let queryParamsSubject: BehaviorSubject<Params>;

  const API_BASE = 'http://localhost:8080/api/v1';
  const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
  const secondAccountId = '74c68bbf-b546-4fed-a1ea-a043fd7219a3';
  const taskQueryParamKeys = [
    'status',
    'cognitiveLoad',
    'deadlineFrom',
    'deadlineTo',
    'maxDuration',
    'searchTerm',
  ] as const;
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

  type TaskQueryParams = Partial<Record<(typeof taskQueryParamKeys)[number], string>>;

  beforeEach(async () => {
    queryParamsSubject = new BehaviorSubject<Params>({});

    (window as RuntimeWindow).__env = {
      apiBasePath: 'http://localhost:8080',
    };

    await TestBed.configureTestingModule({
      imports: [TasksPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParamsSubject.asObservable(),
            snapshot: {
              queryParamMap: { get: (_: string) => null },
            },
          },
        },
        { provide: BASE_PATH, useValue: API_BASE },
        {
          provide: Router,
          useValue: {
            navigate: () => Promise.resolve(true),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TasksPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);

    vi.spyOn(router, 'navigate').mockImplementation(async (_commands, extras) => {
      queryParamsSubject.next(extras?.queryParams ?? {});
      return true;
    });
  });

  afterEach(() => {
    httpMock.verify();
  });

  function tasksForAccount(tasks: TaskResponse[], currentAccountId = accountId): TaskResponse[] {
    return tasks.map((task) => ({
      ...task,
      accountId: task.accountId ?? currentAccountId,
    }));
  }

  function expectTasksRequest(expectedParams: TaskQueryParams = {}) {
    return httpMock.expectOne((request) => {
      if (request.method !== 'GET' || request.url !== `${API_BASE}/tasks`) {
        return false;
      }

      return taskQueryParamKeys.every((key) => {
        const expectedValue = expectedParams[key];
        return expectedValue === undefined
          ? !request.params.has(key)
          : request.params.get(key) === expectedValue;
      });
    });
  }

  function expectNoTasksRequest(): void {
    expect(
      httpMock.match((request) => request.method === 'GET' && request.url === `${API_BASE}/tasks`),
    ).toHaveLength(0);
  }

  async function flushInitialRequests(
    tasksResponses: TaskResponse[] = [],
    accountsResponse: AccountListResponse = { accounts: [] },
    expectedTaskParams: TaskQueryParams = {},
  ): Promise<void> {
    fixture.detectChanges();

    // Match all accounts requests (TasksPageComponent + TaskEditDialogComponent)
    const accountsRequests = httpMock.match(`${API_BASE}/users/accounts`);
    accountsRequests.forEach((req) => req.flush(accountsResponse));

    // Wait for the next microtask so loadTasks() is called
    await Promise.resolve();
    await Promise.resolve();

    const tasksRequest = expectTasksRequest(expectedTaskParams);
    tasksRequest.flush(tasksResponses);

    fixture.detectChanges();
  }

  async function reloadTasksWithResponse(
    tasks: TaskResponse[],
    expectedTaskParams: TaskQueryParams = {},
  ): Promise<void> {
    const loadPromise = component.loadTasks();

    const tasksRequest = expectTasksRequest(expectedTaskParams);
    tasksRequest.flush(tasks);

    await loadPromise;
    fixture.detectChanges();
  }

  it('soll Tasks laden und anzeigen', async () => {
    await flushInitialRequests(
      tasksForAccount([
        {
          id: 't1',
          title: 'Test Aufgabe',
          description: 'Test Description',
          duration: 90,
          status: 'OPEN',
          dependencyIds: [],
        },
      ]),
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
    await flushInitialRequests(
      tasksForAccount([
        {
          id: 't1',
          title: 'Test Aufgabe',
          description: 'Test',
          duration: 90,
          status: 'OPEN',
          dependencyIds: [],
        },
      ]),
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
      tasksForAccount([
        {
          id: 't1',
          title: 'Status-Test',
          duration: 60,
          status: 'OPEN',
          dependencyIds: [],
        },
      ]),
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

    const reloadRequest = expectTasksRequest();
    reloadRequest.flush(
      tasksForAccount([
        {
          id: 't1',
          title: 'Status-Test',
          duration: 60,
          status: 'DONE',
          dependencyIds: [],
        },
      ]),
    );

    await changePromise;
    fixture.detectChanges();
    expect(component.tasks()[0].status).toBe('DONE');
  });

  it('soll Aufgaben nach Status filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'IN_PROGRESS';
    await reloadTasksWithResponse(tasksForAccount([filterTestTasks[1]]), {
      status: 'IN_PROGRESS',
    });

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
    await reloadTasksWithResponse([], {
      status: 'DONE',
      cognitiveLoad: 'HIGH',
    });

    expect(component.tasks()).toEqual([]);
    expect(component.totalTaskCount()).toBe(0);
    expect(component.getEmptyTasksMessage()).toBe(
      'Zu diesem Filter sind keine Aufgaben vorhanden.',
    );
  });

  it('soll Aufgaben nach Anforderungsgrad filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.difficulty = 'HIGH';
    await reloadTasksWithResponse(tasksForAccount([filterTestTasks[1]]), {
      cognitiveLoad: 'HIGH',
    });

    expect(component.tasks().map((task) => task.id)).toEqual(['t-progress-high']);
  });

  it('soll Aufgaben nach Organisation filtern', async () => {
    await flushInitialRequests([], {
      accounts: [
        {
          accountId,
          organizationName: 'GoalDone',
          organizationId: 'org-1',
          roles: [],
          hasConflicts: false,
        },
        {
          accountId: secondAccountId,
          organizationName: 'Partner Org',
          organizationId: 'org-2',
          roles: [],
          hasConflicts: false,
        },
      ],
    });

    component.filters.accountId = secondAccountId;
    await reloadTasksWithResponse([
      ...tasksForAccount(filterTestTasks),
      ...tasksForAccount(
        [
          {
            id: 't-partner',
            title: 'Partner Aufgabe',
            duration: 45,
            status: 'OPEN',
            cognitiveLoad: 'LOW',
            dependencyIds: [],
          },
        ],
        secondAccountId,
      ),
    ]);

    expect(component.tasks().map((task) => task.id)).toEqual(['t-partner']);
    expect(component.tasks()[0].accountLabel).toBe('Partner Org');
  });

  it('soll Aufgaben nach Deadline-Zeitraum filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.deadlineFrom = new Date('2026-05-12T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-18T23:59:59');
    await reloadTasksWithResponse(tasksForAccount([filterTestTasks[1]]), {
      deadlineFrom: '2026-05-12T00:00:00',
      deadlineTo: '2026-05-18T23:59:59',
    });

    expect(component.tasks().map((task) => task.id)).toEqual(['t-progress-high']);
  });

  it('soll Aufgaben ohne Deadline ausfiltern, wenn ein Deadline-Zeitraum gesetzt ist', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.deadlineFrom = new Date('2026-05-01T00:00:00');
    await reloadTasksWithResponse(tasksForAccount(filterTestTasks), {
      deadlineFrom: '2026-05-01T00:00:00',
    });

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
    component.filters.accountId = accountId;
    component.filters.deadlineFrom = new Date('2026-05-19T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-21T23:59:59');
    await reloadTasksWithResponse(
      [
        ...tasksForAccount([filterTestTasks[2]]),
        ...tasksForAccount(
          [
            {
              id: 't-other-account-matching',
              title: 'Gleiche Filterwerte in anderer Organisation',
              duration: 90,
              deadline: '2026-05-20T18:00:00',
              status: 'DONE',
              cognitiveLoad: 'MODERATE',
              dependencyIds: [],
            },
          ],
          secondAccountId,
        ),
      ],
      {
        status: 'DONE',
        cognitiveLoad: 'MODERATE',
        deadlineFrom: '2026-05-19T00:00:00',
        deadlineTo: '2026-05-21T23:59:59',
      },
    );

    expect(component.tasks().map((task) => task.id)).toEqual(['t-done-moderate']);
  });

  it('soll Aufgaben über Freitextsuche in Titel und Beschreibung finden', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.searchTerm = 'Bericht';
    await reloadTasksWithResponse(
      tasksForAccount([
        {
          id: 't-search-title',
          title: 'Projektbericht erstellen',
          description: 'Zusammenfassung für den Kunden',
          duration: 45,
          status: 'OPEN',
          dependencyIds: [],
        },
        {
          id: 't-search-description',
          title: 'Review vorbereiten',
          description: 'Bericht aus dem letzten Sprint prüfen',
          duration: 30,
          status: 'OPEN',
          dependencyIds: [],
        },
      ]),
      { searchTerm: 'Bericht' },
    );

    expect(component.tasks().map((task) => task.id)).toEqual([
      't-search-title',
      't-search-description',
    ]);
  });

  it('soll Aufgaben nach maximaler Dauer filtern', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.maxDuration = 60;
    await reloadTasksWithResponse(tasksForAccount([filterTestTasks[0], filterTestTasks[1]]), {
      maxDuration: '60',
    });

    expect(component.tasks().map((task) => task.id)).toEqual(['t-open-low', 't-progress-high']);
  });

  it('soll Suchbegriff, Status und maximale Dauer kombinieren', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'OPEN';
    component.filters.searchTerm = 'Bericht';
    component.filters.maxDuration = 45;
    await reloadTasksWithResponse(
      tasksForAccount([
        {
          id: 't-combined-search',
          title: 'Projektbericht erstellen',
          description: 'Kurzbericht finalisieren',
          duration: 45,
          status: 'OPEN',
          dependencyIds: [],
        },
      ]),
      {
        status: 'OPEN',
        maxDuration: '45',
        searchTerm: 'Bericht',
      },
    );

    expect(component.tasks().map((task) => task.id)).toEqual(['t-combined-search']);
  });

  it('soll Filter in die URL schreiben und danach gefilterte Aufgaben laden', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.filters.status = 'OPEN';
    component.filters.difficulty = 'LOW';
    component.filters.accountId = secondAccountId;
    component.filters.searchTerm = 'Bericht';
    component.filters.maxDuration = 45;
    component.filters.deadlineFrom = new Date('2026-05-10T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-12T23:59:59');

    component.applyFilterStateToUrl();

    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {
        status: 'OPEN',
        difficulty: 'LOW',
        accountId: secondAccountId,
        searchTerm: 'Bericht',
        maxDuration: 45,
        deadlineFrom: '2026-05-10T00:00:00',
        deadlineTo: '2026-05-12T23:59:59',
      },
      queryParamsHandling: '',
    });

    const tasksRequest = expectTasksRequest({
      status: 'OPEN',
      cognitiveLoad: 'LOW',
      deadlineFrom: '2026-05-10T00:00:00',
      deadlineTo: '2026-05-12T23:59:59',
      maxDuration: '45',
      searchTerm: 'Bericht',
    });
    tasksRequest.flush(
      tasksForAccount(
        [
          {
            id: 't-url-filter',
            title: 'Bericht in Partner-Org',
            duration: 30,
            deadline: '2026-05-11T12:00:00',
            status: 'OPEN',
            cognitiveLoad: 'LOW',
            dependencyIds: [],
          },
        ],
        secondAccountId,
      ),
    );

    await Promise.resolve();
    fixture.detectChanges();

    expect(component.tasks().map((task) => task.id)).toEqual(['t-url-filter']);
  });

  it('soll Filter aus Query-Parametern übernehmen und damit laden', async () => {
    queryParamsSubject.next({
      status: 'IN_PROGRESS',
      difficulty: 'HIGH',
      accountId: secondAccountId,
      searchTerm: 'Bericht',
      maxDuration: '60',
      deadlineFrom: '2026-05-12T00:00:00',
      deadlineTo: '2026-05-18T23:59:59',
    });

    await flushInitialRequests(
      tasksForAccount(
        [
          {
            id: 't-query-param',
            title: 'Bericht abstimmen',
            duration: 60,
            deadline: '2026-05-15T12:00:00',
            status: 'IN_PROGRESS',
            cognitiveLoad: 'HIGH',
            dependencyIds: [],
          },
        ],
        secondAccountId,
      ),
      {
        accounts: [
          {
            accountId: secondAccountId,
            organizationName: 'Partner Org',
            organizationId: 'org-2',
            roles: [],
            hasConflicts: false,
          },
        ],
      },
      {
        status: 'IN_PROGRESS',
        cognitiveLoad: 'HIGH',
        deadlineFrom: '2026-05-12T00:00:00',
        deadlineTo: '2026-05-18T23:59:59',
        maxDuration: '60',
        searchTerm: 'Bericht',
      },
    );

    expect(component.filters).toEqual({
      status: 'IN_PROGRESS',
      difficulty: 'HIGH',
      deadlineFrom: new Date('2026-05-12T00:00:00'),
      deadlineTo: new Date('2026-05-18T23:59:59'),
      accountId: secondAccountId,
      searchTerm: 'Bericht',
      maxDuration: 60,
    });
    expect(component.dateRange).toEqual([
      new Date('2026-05-12T00:00:00'),
      new Date('2026-05-18T23:59:59'),
    ]);
    expect(component.tasks().map((task) => task.id)).toEqual(['t-query-param']);
  });

  it('soll bei ungültigem Deadline-Zeitraum keinen Request abschicken und den letzten Stand behalten', async () => {
    await flushInitialRequests(tasksForAccount([filterTestTasks[0]]), { accounts: [] });

    component.successMessage.set('Die Aufgabe wurde erfolgreich gespeichert.');

    component.filters.deadlineFrom = new Date('2026-12-01T00:00:00');
    component.filters.deadlineTo = new Date('2026-11-01T00:00:00');

    await component.loadTasks();
    fixture.detectChanges();

    expectNoTasksRequest();
    expect(component.tasks().map((task) => task.id)).toEqual(['t-open-low']);
    expect(component.totalTaskCount()).toBe(1);
    expect(component.listErrorMessage()).toBe('Das Startdatum muss vor dem Enddatum liegen');
    expect(component.successMessage()).toBe('');
  });

  it('soll bei ungültigem Deadline-Zeitraum weder URL ändern noch Request abschicken', async () => {
    await flushInitialRequests(tasksForAccount([filterTestTasks[0]]), { accounts: [] });

    component.successMessage.set('Die Aufgabe wurde erfolgreich gespeichert.');
    component.filters.deadlineFrom = new Date('2026-12-01T00:00:00');
    component.filters.deadlineTo = new Date('2026-11-01T00:00:00');

    component.applyFilterStateToUrl();

    expect(router.navigate).not.toHaveBeenCalled();
    expectNoTasksRequest();
    expect(component.tasks().map((task) => task.id)).toEqual(['t-open-low']);
    expect(component.listErrorMessage()).toBe('Das Startdatum muss vor dem Enddatum liegen');
    expect(component.successMessage()).toBe('');
  });

  it('soll bei ungültigen Query-Parametern den letzten Stand behalten', async () => {
    await flushInitialRequests(tasksForAccount([filterTestTasks[0]]), { accounts: [] });

    component.successMessage.set('Die Aufgabe wurde erfolgreich gespeichert.');

    queryParamsSubject.next({
      deadlineFrom: '2026-12-01T00:00:00',
      deadlineTo: '2026-11-01T00:00:00',
    });

    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    expectNoTasksRequest();
    expect(component.tasks().map((task) => task.id)).toEqual(['t-open-low']);
    expect(component.totalTaskCount()).toBe(1);
    expect(component.listErrorMessage()).toBe('Das Startdatum muss vor dem Enddatum liegen');
    expect(component.successMessage()).toBe('');
  });

  it('soll Validierungsfehler nach erfolgreichem Laden automatisch entfernen', async () => {
    await flushInitialRequests(tasksForAccount([filterTestTasks[0]]), { accounts: [] });

    queryParamsSubject.next({
      deadlineFrom: '2026-12-01T00:00:00',
      deadlineTo: '2026-11-01T00:00:00',
    });

    await Promise.resolve();
    await Promise.resolve();

    expect(component.listErrorMessage()).toBe('Das Startdatum muss vor dem Enddatum liegen');

    queryParamsSubject.next({
      deadlineFrom: '2026-05-01T00:00:00',
    });

    const tasksRequest = expectTasksRequest({
      deadlineFrom: '2026-05-01T00:00:00',
    });
    tasksRequest.flush(tasksForAccount(filterTestTasks));

    await Promise.resolve();
    fixture.detectChanges();

    expect(component.listErrorMessage()).toBe('');
    expect(component.successMessage()).toBe('');
    expect(component.tasks().map((task) => task.id)).toEqual([
      't-open-low',
      't-progress-high',
      't-done-moderate',
    ]);
  });

  it('soll Filter zurücksetzen und danach alle Aufgaben anzeigen', async () => {
    await flushInitialRequests([], { accounts: [] });

    component.listErrorMessage.set('Das Startdatum muss vor dem Enddatum liegen');
    component.successMessage.set('Die Aufgabe wurde erfolgreich gespeichert.');
    component.filters.status = 'DONE';
    component.filters.difficulty = 'MODERATE';
    component.filters.accountId = accountId;
    component.filters.searchTerm = 'Bericht';
    component.filters.maxDuration = 90;
    component.filters.deadlineFrom = new Date('2026-05-19T00:00:00');
    component.filters.deadlineTo = new Date('2026-05-21T23:59:59');
    component.dateRange = [component.filters.deadlineFrom, component.filters.deadlineTo];

    component.resetFilters();
    const tasksRequest = expectTasksRequest();
    tasksRequest.flush(tasksForAccount(filterTestTasks));

    await Promise.resolve();
    fixture.detectChanges();

    expect(component.filters).toEqual({
      status: null,
      difficulty: null,
      deadlineFrom: null,
      deadlineTo: null,
      accountId: null,
      searchTerm: null,
      maxDuration: null,
    });
    expect(component.dateRange).toEqual([]);
    expect(component.listErrorMessage()).toBe('');
    expect(component.successMessage()).toBe('');
    expect(component.tasks().map((task) => task.id)).toEqual([
      't-open-low',
      't-progress-high',
      't-done-moderate',
    ]);
  });
});
