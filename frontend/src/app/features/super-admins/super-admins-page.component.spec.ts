// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Observable } from 'rxjs';

import { BASE_PATH, OrgManagementService } from '../../api';
import { SuperAdminsPageComponent } from './super-admins-page.component';

interface OrganizationListItem {
  id: string;
  zitadelOrganizationId: string;
  name: string;
  activeMemberCount: number;
  invitedMemberCount: number;
  createdAt: string;
}

interface OrganizationListResponse {
  organizations: OrganizationListItem[];
}

/**
 * Test double for the future generated OrgManagementService.
 *
 * The real generated service will receive listOrganizations and deleteOrganization
 * after the backend OpenAPI specification has been extended.
 */
@Injectable()
class FutureOrgManagementServiceMock {
  private readonly http = inject(HttpClient);
  private readonly basePath = inject(BASE_PATH);

  listOrganizations(): Observable<OrganizationListResponse> {
    return this.http.get<OrganizationListResponse>(`${this.basePath}/admins/organizations`);
  }

  deleteOrganization(organizationId: string): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/admins/organizations/${organizationId}`);
  }
}

describe('SuperAdminsPageComponent', () => {
  let fixture: ComponentFixture<SuperAdminsPageComponent>;
  let component: SuperAdminsPageComponent;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  const organization: OrganizationListItem = {
    id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    zitadelOrganizationId: 'org-123',
    name: 'GoalDone GmbH',
    activeMemberCount: 2,
    invitedMemberCount: 1,
    createdAt: '2026-01-01T12:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SuperAdminsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        { provide: OrgManagementService, useClass: FutureOrgManagementServiceMock },
        ConfirmationService,
        MessageService,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SuperAdminsPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(options?: {
    admins?: unknown[];
    organizations?: OrganizationListItem[];
  }): void {
    fixture.detectChanges();

    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush(options?.admins ?? []);

    const organizationsRequest = httpMock.expectOne(`${API_BASE}/admins/organizations`);
    organizationsRequest.flush({
      organizations: options?.organizations ?? [],
    });

    fixture.detectChanges();
  }

  it('should create', () => {
    flushInitialRequests();

    expect(component).toBeTruthy();
  });

  it('should show empty organization message when no organizations exist', () => {
    flushInitialRequests({
      organizations: [],
    });

    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Es sind aktuell keine Unternehmen vorhanden.');
  });

  it('should display existing organizations', () => {
    flushInitialRequests({
      organizations: [organization],
    });

    const text = fixture.nativeElement.textContent;

    expect(text).toContain('GoalDone GmbH');
    expect(text).toContain('Aktive Mitglieder:');
    expect(text).toContain('Eingeladen:');
  });

  it('should reload organizations after organization was created', () => {
    flushInitialRequests({
      organizations: [],
    });

    component.onOrganizationCreated();

    const organizationsRequest = httpMock.expectOne(`${API_BASE}/admins/organizations`);
    organizationsRequest.flush({
      organizations: [organization],
    });

    expect(component.organizations()).toEqual([organization]);
  });

  it('should open confirmation dialog before deleting an organization', () => {
    flushInitialRequests({
      organizations: [organization],
    });

    const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
    const confirmSpy = vi.spyOn(confirmationService, 'confirm');

    component.confirmDeleteOrganization(organization);

    expect(confirmSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        header: 'Unternehmen löschen',
        acceptLabel: 'Löschen',
        rejectLabel: 'Abbrechen',
      }),
    );
  });

  it('should delete organization after confirmation', () => {
    flushInitialRequests({
      organizations: [organization],
    });

    const confirmationService = fixture.debugElement.injector.get(ConfirmationService);

    vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation: any) => {
      confirmation.accept();
      return confirmationService;
    });

    component.confirmDeleteOrganization(organization);

    const deleteRequest = httpMock.expectOne(`${API_BASE}/admins/organizations/${organization.id}`);

    expect(deleteRequest.request.method).toBe('DELETE');

    deleteRequest.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    expect(component.organizations()).toEqual([]);
  });

  it('should keep organization in list when delete fails', () => {
    flushInitialRequests({
      organizations: [organization],
    });

    const confirmationService = fixture.debugElement.injector.get(ConfirmationService);

    vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation: any) => {
      confirmation.accept();
      return confirmationService;
    });

    component.confirmDeleteOrganization(organization);

    const deleteRequest = httpMock.expectOne(`${API_BASE}/admins/organizations/${organization.id}`);

    deleteRequest.flush(
      { detail: 'FORBIDDEN' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );

    expect(component.organizations()).toEqual([organization]);
  });

  it('should disable delete button when only one super admin exists', () => {
    fixture.detectChanges();

    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush([
      { zitadelId: '1', email: 'admin@test.com', firstName: 'Admin', lastName: 'One' },
    ]);

    const organizationsRequest = httpMock.expectOne(`${API_BASE}/admins/organizations`);
    organizationsRequest.flush({
      organizations: [],
    });

    fixture.detectChanges();

    const trashIcon = fixture.debugElement.query(By.css('.pi-trash'));
    const deleteButton = trashIcon.parent?.nativeElement;

    expect(deleteButton.disabled).toBe(true);
  });

  it('should show specific error message when last super admin deletion fails', () => {
    const messageService = fixture.debugElement.injector.get(MessageService);
    const messageSpy = vi.spyOn(messageService, 'add');

    fixture.detectChanges();

    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush([
      { zitadelId: '1', email: 'admin1@test.com' },
      { zitadelId: '2', email: 'admin2@test.com' },
    ]);

    const organizationsRequest = httpMock.expectOne(`${API_BASE}/admins/organizations`);
    organizationsRequest.flush({
      organizations: [],
    });

    fixture.detectChanges();

    (component as any).deleteAdmin({ zitadelId: '1', email: 'admin1@test.com' });

    const deleteReq = httpMock.expectOne(`${API_BASE}/admins/super-admins/1`);

    deleteReq.flush(
      { detail: 'LAST_SUPER_ADMIN_CANNOT_BE_DELETED' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(messageSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail:
          'Der letzte Super-Admin kann nicht gelöscht werden. Es muss mindestens ein Administrator im System verbleiben.',
      }),
    );
  });
});
