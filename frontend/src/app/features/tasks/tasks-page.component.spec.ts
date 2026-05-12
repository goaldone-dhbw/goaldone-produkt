import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';

import { BASE_PATH } from '../../api';
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

    await TestBed.configureTestingModule({
      imports: [TasksPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },

        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: () => null,
              },
            },
          },
        },
        {
          provide: Router,
          useValue: {
            navigate: vi.fn(),
          },
        },
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

  it('soll Tasks laden und anzeigen', async () => {
    const accountId = '8836327e-02e8-4539-9c3d-6ca434d43827';
    await flushInitialRequests(
      [
        {
          accountId: accountId,
          tasks: [
            {
              id: 't1',
              title: 'Test Aufgabe',
              description: 'Test Description',
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
          accountId: accountId,
          tasks: [
            {
              id: 't1',
              title: 'Test Aufgabe',
              description: 'Test',
              duration: 90,
              deadline: null,
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
  });
});
