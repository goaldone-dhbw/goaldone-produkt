import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH } from '../../api';
import { AccountStateService } from '../../core/services/account-state.service';
import { OrgContextService } from '../../core/services/org-context.service';
import { WorkingHoursPage } from './working-hours.page';
import { ConfirmationService, MessageService } from 'primeng/api';

describe('WorkingHoursPage', () => {
  let fixture: ComponentFixture<WorkingHoursPage>;
  let component: WorkingHoursPage;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(async () => {
    const orgContextServiceMock = {
      getDefaultOrg: () => ({ id: 'org-1', slug: 'test-org', role: 'USER' }),
      getDialogOrg: () => null,
      getSettingsOrg: () => null,
    };

    const accountStateServiceMock = {
      refresh: () => {},
      hasConflicts: { value: false },
    };

    await TestBed.configureTestingModule({
      imports: [WorkingHoursPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        { provide: OrgContextService, useValue: orgContextServiceMock },
        { provide: AccountStateService, useValue: accountStateServiceMock },
        ConfirmationService,
        MessageService
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkingHoursPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(): void {
    fixture.detectChanges();

    const accountsRequest = httpMock.expectOne(`${API_BASE}/users/accounts`);
    accountsRequest.flush({ accounts: [] });

    const workingTimesRequest = httpMock.expectOne(`${API_BASE}/working-times`);
    workingTimesRequest.flush({ items: [] });

    fixture.detectChanges();
  }

  it('should create', () => {
    flushInitialRequests();
    expect(component).toBeTruthy();
  });

  it('should display empty message when no working hours exist', () => {
    flushInitialRequests();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Es sind noch keine Arbeitszeiten hinterlegt.');
  });
});
