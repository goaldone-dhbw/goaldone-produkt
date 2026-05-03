import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';

/**
 * Global error notification service for displaying user-friendly error and success messages
 * via PrimeNG Toast notifications.
 *
 * Usage:
 *   this.errorNotificationService.showError('Something went wrong.');
 *   this.errorNotificationService.showSuccess('Operation completed.');
 *
 * Requires MessageService to be provided in the root injector (app.config.ts) and
 * <p-toast /> to be present in the root app template (app.html).
 */
@Injectable({ providedIn: 'root' })
export class ErrorNotificationService {
  private readonly messageService = inject(MessageService);

  /**
   * Show an error toast notification.
   * @param message The user-friendly error message to display.
   * @param title Optional title for the toast (defaults to 'Fehler').
   */
  showError(message: string, title: string = 'Fehler'): void {
    this.messageService.add({
      severity: 'error',
      summary: title,
      detail: message,
      life: 5000,
    });
  }

  /**
   * Show a success toast notification.
   * @param message The success message to display.
   * @param title Optional title for the toast (defaults to 'Erfolg').
   */
  showSuccess(message: string, title: string = 'Erfolg'): void {
    this.messageService.add({
      severity: 'success',
      summary: title,
      detail: message,
      life: 3000,
    });
  }

  /**
   * Show a warning toast notification.
   * @param message The warning message to display.
   * @param title Optional title for the toast (defaults to 'Warnung').
   */
  showWarn(message: string, title: string = 'Warnung'): void {
    this.messageService.add({
      severity: 'warn',
      summary: title,
      detail: message,
      life: 4000,
    });
  }
}
