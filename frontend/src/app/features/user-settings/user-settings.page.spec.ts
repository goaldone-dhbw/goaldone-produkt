import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { UserSettingsAccountService } from '../../core/services/user-settings-account.service';
import { BASE_PATH } from '../../api';

describe('UserSettingsAccountService', () => {
  let service: UserSettingsAccountService;
  let httpMock: HttpTestingController;

  describe('without BASE_PATH provider', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          UserSettingsAccountService,
          provideHttpClient(),
          provideHttpClientTesting(),
        ],
      });

      service = TestBed.inject(UserSettingsAccountService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    describe('changePassword()', () => {
      it('sends PUT to the fallback base path', () => {
        service.changePassword({ oldPassword: 'Old1!pass', newPassword: 'New1!pass' }).subscribe();

        const req = httpMock.expectOne('http://localhost:8080/api/v1/users/accounts/password');

        expect(req.request.method).toBe('PUT');

        req.flush(null);
      });

      it('includes currentPassword and newPassword in the request body', () => {
        const payload = { oldPassword: 'Old1!pass', newPassword: 'New1!pass' };

        service.changePassword(payload).subscribe();

        const req = httpMock.expectOne('http://localhost:8080/api/v1/users/accounts/password');

        expect(req.request.body).toEqual({ currentPassword: 'Old1!pass', newPassword: 'New1!pass' });

        req.flush(null);
      });

      it('completes the observable on success', () => {
        let completed = false;

        service.changePassword({ oldPassword: 'Old1!pass', newPassword: 'New1!pass' }).subscribe({
          complete: () => {
            completed = true;
          },
        });

        httpMock.expectOne('http://localhost:8080/api/v1/users/accounts/password').flush(null);

        expect(completed).toBe(true);
      });

      it('forwards HTTP errors to subscribers', () => {
        let receivedStatus: number | undefined;

        service.changePassword({ oldPassword: 'wrong', newPassword: 'New1!pass' }).subscribe({
          error: (err) => {
            receivedStatus = err.status;
          },
        });

        httpMock
          .expectOne('http://localhost:8080/api/v1/users/accounts/password')
          .flush(null, { status: 401, statusText: 'Unauthorized' });

        expect(receivedStatus).toBe(401);
      });
    });

    describe('logout()', () => {
      it('returns an observable without making an HTTP request', () => {
        service.logout().subscribe();

        httpMock.expectNone('http://localhost:8080/api/v1/auth/logout');
      });

      it('completes immediately', () => {
        let completed = false;

        service.logout().subscribe({
          complete: () => {
            completed = true;
          },
        });

        expect(completed).toBe(true);
      });
    });

    describe('deleteOwnAccount()', () => {
      it('sends DELETE to the fallback base path with accountId', () => {
        const accountId = '123e4567-e89b-12d3-a456-426614174000';
        service.deleteOwnAccount(accountId).subscribe();

        const req = httpMock.expectOne(`http://localhost:8080/api/v1/users/accounts/${accountId}`);

        expect(req.request.method).toBe('DELETE');

        req.flush(null);
      });

      it('completes the observable on success', () => {
        let completed = false;

        const accountId = '123e4567-e89b-12d3-a456-426614174000';
        service.deleteOwnAccount(accountId).subscribe({
          complete: () => {
            completed = true;
          },
        });

        httpMock.expectOne(`http://localhost:8080/api/v1/users/accounts/${accountId}`).flush(null);

        expect(completed).toBe(true);
      });

      it('forwards HTTP errors to subscribers', () => {
        let receivedStatus: number | undefined;

        const accountId = '123e4567-e89b-12d3-a456-426614174000';
        service.deleteOwnAccount(accountId).subscribe({
          error: (err) => {
            receivedStatus = err.status;
          },
        });

        httpMock
          .expectOne(`http://localhost:8080/api/v1/users/accounts/${accountId}`)
          .flush(null, { status: 403, statusText: 'Forbidden' });

        expect(receivedStatus).toBe(403);
      });
    });
  });

  describe('with BASE_PATH provider as string', () => {
    const customBasePath = 'https://api.example.com';

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          UserSettingsAccountService,
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: BASE_PATH, useValue: customBasePath },
        ],
      });

      service = TestBed.inject(UserSettingsAccountService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('uses the injected BASE_PATH for changePassword', () => {
      service.changePassword({ oldPassword: 'Old1!pass', newPassword: 'New1!pass' }).subscribe();

      const req = httpMock.expectOne(`${customBasePath}/users/accounts/password`);

      expect(req.request.method).toBe('PUT');

      req.flush(null);
    });

    it('uses the injected BASE_PATH for logout', () => {
      service.logout().subscribe();

      httpMock.expectNone(`${customBasePath}/auth/logout`);
    });

    it('uses the injected BASE_PATH for deleteOwnAccount', () => {
      const accountId = '123e4567-e89b-12d3-a456-426614174000';
      service.deleteOwnAccount(accountId).subscribe();

      const req = httpMock.expectOne(`${customBasePath}/users/accounts/${accountId}`);

      expect(req.request.method).toBe('DELETE');

      req.flush(null);
    });
  });

  describe('with BASE_PATH provider as array', () => {
    const customBasePath = 'https://array-api.example.com';

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          UserSettingsAccountService,
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: BASE_PATH, useValue: [customBasePath] },
        ],
      });

      service = TestBed.inject(UserSettingsAccountService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('uses the first BASE_PATH entry when BASE_PATH is provided as an array', () => {
      service.changePassword({ oldPassword: 'Old1!pass', newPassword: 'New1!pass' }).subscribe();

      const req = httpMock.expectOne(`${customBasePath}/users/accounts/password`);

      expect(req.request.method).toBe('PUT');

      req.flush(null);
    });
  });
});
