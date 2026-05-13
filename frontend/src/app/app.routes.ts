import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';
import { superAdminGuard } from './core/auth/super-admin.guard';

export const routes: Routes = [
  {
    path: 'app',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () =>
      import('./core/layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/mainpage/mainpage').then((m) => m.MainPage),
      },
      {
        path: 'tasks',
        loadComponent: () =>
          import('./features/tasks/tasks-page.component').then((m) => m.TasksPageComponent),
      },
      {
        path: 'schedule',
        loadComponent: () =>
          import('./features/schedule/schedule.page').then((m) => m.SchedulePage),
      },
      {
        path: 'appointments',
        loadComponent: () =>
          import('./features/appointments/appointments.page').then((m) => m.AppointmentsPage),
      },
      {
        path: 'working-hours',
        loadComponent: () =>
          import('./features/working-hours/working-hours.page').then((m) => m.WorkingHoursPage),
      },
      {
        path: 'organization',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/org-settings/org-settings.page').then((m) => m.OrgSettingsPage),
      },
      {
        path: 'super-admin',
        canActivate: [superAdminGuard],
        loadComponent: () =>
          import('./features/super-admins/super-admins-page.component').then((m) => m.SuperAdminsPageComponent),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/user-settings/user-settings.page').then((m) => m.UserSettingsPage),
      },
    ],
  },
  {
    path: '',
    loadComponent: () =>
      import('./features/startpage/start-page.component').then((m) => m.StartPageComponent),
  },
  {
    path: 'callback',
    loadComponent: () =>
      import('./features/callback/callback-page.component').then((m) => m.CallbackPageComponent),
  },
  {
    path: 'link-callback',
    loadComponent: () =>
      import('./features/callback/account-link-callback/account-link-callback.page').then((m) => m.AccountLinkCallbackPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
