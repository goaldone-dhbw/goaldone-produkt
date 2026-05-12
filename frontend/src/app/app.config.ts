import {
  ApplicationConfig,
  APP_INITIALIZER,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { BASE_PATH } from './api';
import { providePrimeNG } from 'primeng/config';
import { GoaldoneTheme } from './GoaldoneTheme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    providePrimeNG({
      theme: {
        preset: GoaldoneTheme,
        options: {
          darkModeSelector: '[data-theme="dark"]',
        },
      },
      translation: {
        dayNames: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
        dayNamesShort: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
        dayNamesMin: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
        monthNames: [
          'Januar',
          'Februar',
          'März',
          'April',
          'Mai',
          'Juni',
          'Juli',
          'August',
          'September',
          'Oktober',
          'November',
          'Dezember',
        ],
        monthNamesShort: [
          'Jan',
          'Feb',
          'Mär',
          'Apr',
          'Mai',
          'Jun',
          'Jul',
          'Aug',
          'Sep',
          'Okt',
          'Nov',
          'Dez',
        ],
        dateFormat: 'dd.mm.yy',
        firstDayOfWeek: 1,
        today: 'Heute',
        clear: 'Löschen',
        weekHeader: 'KW',
        chooseDate: 'Datum auswählen',
        chooseMonth: 'Monat auswählen',
        chooseYear: 'Jahr auswählen',
        prevMonth: 'Vorheriger Monat',
        nextMonth: 'Nächster Monat',
        prevYear: 'Vorheriges Jahr',
        nextYear: 'Nächstes Jahr',
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
