import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, Optional } from '@angular/core';
import { Observable, of } from 'rxjs';
import { BASE_PATH } from '../../api';

/**
 * Request body for changing the password of the currently authenticated user.
 */
export interface ChangePasswordRequest {
  /**
   * Current password of the user.
   */
  oldPassword: string;

  /**
   * New password that should replace the current password.
   */
  newPassword: string;
}

/**
 * Provides HTTP methods for account-related actions in the user settings page.
 *
 * This service is used as a temporary frontend integration service until the
 * corresponding backend endpoints are available in the generated OpenAPI client.
 */
@Injectable({
  providedIn: 'root',
})
export class UserSettingsAccountService {
  /**
   * Fallback API base path used during local development.
   * Must include /api/v1 to match the backend context path.
   */
  private readonly fallbackBasePath = 'http://localhost:8080/api/v1';

  /**
   * Creates the service and injects the HTTP client and optional API base path.
   *
   * @param httpClient Angular HTTP client used for REST requests.
   * @param basePath Optional API base path provided by the OpenAPI setup.
   */
  constructor(
    private httpClient: HttpClient,
    @Optional() @Inject(BASE_PATH) private basePath: string | string[] | null,
  ) {}

  /**
   * Sends a request to change the password of the currently authenticated user.
   *
   * Maps frontend field names (oldPassword) to the backend's expected field names
   * (currentPassword) and uses PUT /users/accounts/password as defined in the OpenAPI spec.
   *
   * @param request Object containing the current password and the new password.
   * @returns Observable that completes when the password was changed successfully.
   */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.httpClient.put<void>(`${this.apiBasePath}/users/accounts/password`, {
      currentPassword: request.oldPassword,
      newPassword: request.newPassword,
    });
  }

  /**
   * No backend logout endpoint exists — logout is handled entirely by Zitadel via the
   * AuthService. This method completes immediately so callers can follow the same
   * observable pattern without changes.
   *
   * @returns Observable that completes immediately.
   */
  logout(): Observable<void> {
    return of(undefined);
  }

  /**
   * Sends a request to delete a specific account by its ID.
   *
   * Uses DELETE /users/accounts/{accountId} as defined in the OpenAPI spec.
   * The caller must supply the accountId of the account to delete.
   *
   * @param accountId UUID of the account to delete.
   * @returns Observable that completes when the account was deleted successfully.
   */
  deleteOwnAccount(accountId: string): Observable<void> {
    return this.httpClient.delete<void>(`${this.apiBasePath}/users/accounts/${accountId}`);
  }

  /**
   * Resolves the API base path.
   *
   * @returns Configured base path or local fallback path.
   */
  private get apiBasePath(): string {
    const resolvedBasePath = Array.isArray(this.basePath) ? this.basePath[0] : this.basePath;
    return resolvedBasePath || this.fallbackBasePath;
  }
}
