// @vitest-environment jsdom

/**
 * Unit tests for CreateOrganizationCardComponent.
 *
 * The tests verify client-side validation, prevention of invalid API requests,
 * correct request payload creation, loading state handling and user-friendly
 * error mapping for backend responses.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';

import { BASE_PATH } from '../../../../api';
import { CreateOrganizationCardComponent } from './create-organization-card';

describe('CreateOrganizationCardComponent', () => {
  let fixture: ComponentFixture<CreateOrganizationCardComponent>;
  let component: CreateOrganizationCardComponent;
  let httpMock: HttpTestingController;
  let messageService: MessageService;

  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateOrganizationCardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        MessageService,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateOrganizationCardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    messageService = TestBed.inject(MessageService);

    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should accept valid organization and admin data', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    expect(component.form.valid).toBe(true);
  });

  it('should reject invalid admin email and not send api request', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'abc',
    });

    component.submit();

    expect(component.form.invalid).toBe(true);
    httpMock.expectNone(`${API_BASE}/admins/organizations`);
  });

  it('should reject empty required fields and not send api request', () => {
    component.form.setValue({
      name: '',
      adminFirstName: '',
      adminLastName: '',
      adminEmail: '',
    });

    component.submit();

    expect(component.form.invalid).toBe(true);
    httpMock.expectNone(`${API_BASE}/admins/organizations`);
  });

  it('should reject organization name with only spaces and not send api request', () => {
    component.form.setValue({
      name: '   ',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    expect(component.form.invalid).toBe(true);
    httpMock.expectNone(`${API_BASE}/admins/organizations`);
  });

  it('should reject admin first name with only spaces and not send api request', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: '   ',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    expect(component.form.invalid).toBe(true);
    httpMock.expectNone(`${API_BASE}/admins/organizations`);
  });

  it('should reject admin last name with only spaces and not send api request', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: '   ',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    expect(component.form.invalid).toBe(true);
    httpMock.expectNone(`${API_BASE}/admins/organizations`);
  });

  it('should send api request when form is valid', () => {
    const messageSpy = vi.spyOn(messageService, 'add');

    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    request.flush({
      id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      zitadelOrganizationId: 'org-123',
      name: 'GoalDone GmbH',
      adminEmail: 'admin@goaldone.de',
      createdAt: '2026-01-01T12:00:00Z',
    });

    expect(messageSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
        detail: 'Die Einladung wurde an admin@goaldone.de versendet.',
      }),
    );

    expect(component.successMessage()).toContain('admin@goaldone.de');
  });

  it('should trim text values before sending api request', () => {
    component.form.setValue({
      name: '  GoalDone GmbH  ',
      adminFirstName: '  Max  ',
      adminLastName: '  Mustermann  ',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    expect(request.request.body).toEqual({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    request.flush({
      id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      zitadelOrganizationId: 'org-123',
      name: 'GoalDone GmbH',
      adminEmail: 'admin@goaldone.de',
      createdAt: '2026-01-01T12:00:00Z',
    });
  });

  it('should show loading state during request', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    expect(component.isSubmitting()).toBe(true);

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush({
      id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      zitadelOrganizationId: 'org-123',
      name: 'GoalDone GmbH',
      adminEmail: 'admin@goaldone.de',
      createdAt: '2026-01-01T12:00:00Z',
    });

    expect(component.isSubmitting()).toBe(false);
  });

  it('should show specific message when email already exists', () => {
    const messageSpy = vi.spyOn(messageService, 'add');

    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'existing@example.com',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush({ detail: 'EMAIL_ALREADY_IN_USE' }, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toBe(
      'Diese E-Mail ist bereits einem anderen Account zugeordnet.',
    );

    expect(messageSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'Diese E-Mail ist bereits einem anderen Account zugeordnet.',
      }),
    );
  });

  it('should show specific message when organization name already exists', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush(
      { detail: 'ORGANIZATION_NAME_ALREADY_EXISTS' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(component.errorMessage()).toBe('Dieser Unternehmensname ist bereits vergeben.');
  });

  it('should show validation error message on 400', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush({ detail: 'VALIDATION_ERROR' }, { status: 400, statusText: 'Bad Request' });

    expect(component.errorMessage()).toBe(
      'Bitte prüfe die Eingaben. Mindestens ein Feld ist ungültig.',
    );
  });

  it('should show unauthorized message on 401', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush({ detail: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });

    expect(component.errorMessage()).toBe('Du bist nicht angemeldet. Bitte melde dich erneut an.');
  });

  it('should show forbidden message on 403', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush({ detail: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });

    expect(component.errorMessage()).toBe('Du hast keine Berechtigung, Unternehmen anzulegen.');
  });

  it('should show zitadel error message on 502', () => {
    component.form.setValue({
      name: 'GoalDone GmbH',
      adminFirstName: 'Max',
      adminLastName: 'Mustermann',
      adminEmail: 'admin@goaldone.de',
    });

    component.submit();

    const request = httpMock.expectOne(`${API_BASE}/admins/organizations`);

    request.flush({ detail: 'ZITADEL_UPSTREAM_ERROR' }, { status: 502, statusText: 'Bad Gateway' });

    expect(component.errorMessage()).toBe(
      'Das Unternehmen konnte wegen eines Fehlers bei Zitadel nicht angelegt werden. Es wurden keine unvollständigen Daten gespeichert.',
    );
  });
});
