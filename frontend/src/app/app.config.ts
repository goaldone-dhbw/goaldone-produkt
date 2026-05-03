import {
  ApplicationConfig,
  APP_INITIALIZER,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { MessageService } from 'primeng/api';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { tenantInterceptor } from './core/interceptors/tenant.interceptor';
import { AuthService } from './core/auth/auth.service';
import { BASE_PATH } from './api';
import { providePrimeNG } from 'primeng/config';
import { GoaldoneTheme } from './GoaldoneTheme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // PrimeNG MessageService for global toast notifications (used by ErrorNotificationService)
    MessageService,
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, tenantInterceptor])),
    providePrimeNG({
      theme: {
        preset: GoaldoneTheme,
        options: {
          darkModeSelector: '[data-theme="dark"]',
        },
      },
    }),
    provideOAuthClient(),
    // Read BASE_PATH at runtime from window.__env (set by env.js before app boots)
    {
      provide: BASE_PATH,
      useFactory: () => {
        const windowEnv = (window as any).__env || {};
        return windowEnv['apiBasePath'] || 'http://localhost:8080';
      },
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (auth: AuthService) => () => auth.initialize(),
      deps: [AuthService],
      multi: true,
    },
  ],
};
