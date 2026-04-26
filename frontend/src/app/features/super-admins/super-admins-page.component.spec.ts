// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { BASE_PATH } from '../../api';
import { SuperAdminsPageComponent } from './super-admins-page.component';
import { ConfirmationService, MessageService } from 'primeng/api';

describe('SuperAdminsPageComponent', () => {
  let fixture: ComponentFixture<SuperAdminsPageComponent>;
  let component: SuperAdminsPageComponent;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SuperAdminsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        ConfirmationService,
        MessageService
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SuperAdminsPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(): void {
    fixture.detectChanges();

    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush([]);

    fixture.detectChanges();
  }

  it('should create', () => {
    flushInitialRequests();
    expect(component).toBeTruthy();
  });

  it('should disable delete button when only one super admin exists', () => {
    fixture.detectChanges();
    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush([{ zitadelId: '1', email: 'admin@test.com', firstName: 'Admin', lastName: 'One' }]);
    fixture.detectChanges();

    // Look for the button that contains the trash icon
    const trashIcon = fixture.debugElement.query(By.css('.pi-trash'));
    const deleteButton = trashIcon.parent?.nativeElement;
    expect(deleteButton.disabled).toBe(true);
  });

  it('should show specific error message when last super admin deletion fails', () => {
    // Get the MessageService instance from the component's injector because it's provided at component level
    const messageService = fixture.debugElement.injector.get(MessageService);
    const messageSpy = vi.spyOn(messageService, 'add');

    // Trigger initial load
    fixture.detectChanges();
    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush([
      { zitadelId: '1', email: 'admin1@test.com' },
      { zitadelId: '2', email: 'admin2@test.com' }
    ]);
    fixture.detectChanges();

    // Call deleteAdmin directly to test error handling
    (component as any).deleteAdmin({ zitadelId: '1', email: 'admin1@test.com' });

    const deleteReq = httpMock.expectOne(`${API_BASE}/admins/super-admins/1`);
    deleteReq.flush(
      { detail: 'LAST_SUPER_ADMIN_CANNOT_BE_DELETED' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(messageSpy).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'Der letzte Super-Admin kann nicht gelöscht werden. Es muss mindestens ein Administrator im System verbleiben.'
    }));
  });
});
