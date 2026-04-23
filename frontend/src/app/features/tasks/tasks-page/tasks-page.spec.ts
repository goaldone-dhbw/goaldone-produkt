import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TasksPageComponent } from './tasks-page';

describe('TasksPageComponent', () => {
  let fixture: ComponentFixture<TasksPageComponent>;
  let component: TasksPageComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    (window as any).__env = {
      apiBasePath: 'http://localhost:8080',
    };

    await TestBed.configureTestingModule({
      imports: [TasksPageComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(TasksPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(tasks: any[] = [], accountsResponse: any = { accounts: [] }): void {
    fixture.detectChanges();

    const accountsRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/users/accounts',
    );
    accountsRequest.flush(accountsResponse);

    const tasksRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/tasks',
    );
    tasksRequest.flush(tasks);

    fixture.detectChanges();
  }

  function getTextContent(): string {
    return fixture.nativeElement.textContent ?? '';
  }

  it('soll eine Aufgabe erstellen und danach in der Liste anzeigen', async () => {
    flushInitialRequests([], {
      accounts: [
        {
          accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
          organizationId: 'ed4fef73-5b25-45f9-9779-a83363593719',
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
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
      dependencyIds: [],
    });

    const savePromise = component.saveTask();

    const postRequest = httpMock.expectOne(
      (req) => req.method === 'POST' && req.url === 'http://localhost:8080/tasks',
    );

    expect(postRequest.request.body).toMatchObject({
      title: 'Dokumentation schreiben',
      description: 'Feature dokumentieren',
      duration: 120,
      status: 'OPEN',
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
      dependencyIds: [],
    });

    postRequest.flush({
      id: '00000000-0000-0000-0000-000000000001',
      title: 'Dokumentation schreiben',
      description: 'Feature dokumentieren',
      duration: 120,
      deadline: '2026-04-25T12:00:00.000Z',
      status: 'OPEN',
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
      dependencyIds: [],
    });

    const reloadRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/tasks',
    );
    reloadRequest.flush([
      {
        id: '00000000-0000-0000-0000-000000000001',
        title: 'Dokumentation schreiben',
        description: 'Feature dokumentieren',
        duration: 120,
        deadline: '2026-04-25T12:00:00.000Z',
        status: 'OPEN',
        accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
        dependencyIds: [],
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
    flushInitialRequests(
      [
        {
          id: 't1',
          title: 'Alte Aufgabe',
          description: 'Alt',
          duration: 90,
          deadline: null,
          status: 'OPEN',
          accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
          dependencyIds: [],
        },
      ],
      {
        accounts: [
          {
            accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
            organizationId: 'ed4fef73-5b25-45f9-9779-a83363593719',
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
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
      dependencyIds: [],
    });

    const savePromise = component.saveTask();

    const putRequest = httpMock.expectOne(
      (req) => req.method === 'PUT' && req.url === 'http://localhost:8080/tasks/t1',
    );

    expect(putRequest.request.body).toMatchObject({
      title: 'Aktualisierte Aufgabe',
      duration: 150,
      status: 'IN_PROGRESS',
      description: 'Neu',
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
      dependencyIds: [],
    });

    putRequest.flush({
      id: 't1',
      title: 'Aktualisierte Aufgabe',
    });

    const reloadRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/tasks',
    );
    reloadRequest.flush([
      {
        id: 't1',
        title: 'Aktualisierte Aufgabe',
        description: 'Neu',
        duration: 150,
        deadline: null,
        status: 'IN_PROGRESS',
        accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
        dependencyIds: [],
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
    flushInitialRequests([], { accounts: [] });

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

    const postRequests = httpMock.match(
      (req) => req.method === 'POST' && req.url === 'http://localhost:8080/tasks',
    );

    expect(postRequests.length).toBe(0);
    expect(component.formErrorMessage()).toContain('Pflichtfelder');
    expect(getTextContent()).toContain('Bitte fülle alle Pflichtfelder korrekt aus.');
  });

  it('soll bei nicht erreichbarem Backend eine Fehlermeldung im UI anzeigen', () => {
    fixture.detectChanges();

    const accountsRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/users/accounts',
    );
    accountsRequest.flush({ accounts: [] });

    const tasksRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/tasks',
    );
    tasksRequest.error(new ProgressEvent('Network error'));

    fixture.detectChanges();

    expect(component.listErrorMessage()).toContain('Aufgaben konnten nicht geladen werden');
    expect(getTextContent()).toContain('Aufgaben konnten nicht geladen werden');
  });

  it('soll den Status einer Aufgabe ändern und speichern', async () => {
    flushInitialRequests(
      [
        {
          id: 't1',
          title: 'Status-Test',
          description: null,
          duration: 60,
          deadline: null,
          status: 'OPEN',
          accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
          dependencyIds: [],
        },
      ],
      {
        accounts: [
          {
            accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
            organizationId: 'ed4fef73-5b25-45f9-9779-a83363593719',
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

    const putRequest = httpMock.expectOne(
      (req) => req.method === 'PUT' && req.url === 'http://localhost:8080/tasks/t1',
    );
    expect(putRequest.request.body).toMatchObject({
      status: 'DONE',
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
    });
    putRequest.flush({});

    const reloadRequest = httpMock.expectOne(
      (req) => req.method === 'GET' && req.url === 'http://localhost:8080/tasks',
    );
    reloadRequest.flush([
      {
        id: 't1',
        title: 'Status-Test',
        description: null,
        duration: 60,
        deadline: null,
        status: 'DONE',
        accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
        dependencyIds: [],
      },
    ]);

    await changePromise;
    fixture.detectChanges();

    expect(component.tasks()[0].status).toBe('DONE');
    expect(component.successMessage()).toContain('Status wurde gespeichert');
  });

  it('soll einen Validierungsfehler anzeigen, wenn duration 0 ist', async () => {
    flushInitialRequests([], {
      accounts: [
        {
          accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
          organizationId: 'ed4fef73-5b25-45f9-9779-a83363593719',
          organizationName: 'GoalDone',
        },
      ],
    });

    component.openCreateDialog();
    fixture.detectChanges();

    component.taskForm.patchValue({
      title: 'Dauer-Test',
      duration: 0,
      status: 'OPEN',
      accountId: '8836327e-02e8-4539-9c3d-6ca434d43827',
      dependencyIds: [],
    });

    component.taskForm.markAllAsTouched();
    fixture.detectChanges();

    expect(component.taskForm.get('duration')?.invalid).toBeTruthy();
  });
});
