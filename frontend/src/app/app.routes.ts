import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { StartPageComponent } from './features/startpage/start-page.component';
import { TasksPageComponent } from './features/tasks/tasks-page.component';
import { CallbackPageComponent } from './features/callback/callback-page.component';
import { SuperAdminsPageComponent } from './features/super-admins/super-admins-page.component';

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
        component: TasksPageComponent
      },
      {
        path: 'schedule',
        loadComponent: () =>
          import('./features/schedule/schedule.page').then((m) => m.SchedulePage),
      },
      {
        path: 'working-hours',
        loadComponent: () =>
          import('./features/working-hours/working-hours.page').then((m) => m.WorkingHoursPage),
      },
      {
        path: 'organization',
        loadComponent: () =>
          import('./features/org-settings/org-settings.page').then((m) => m.OrgSettingsPage),
      },
      {
        path: 'super-admin',
        component: SuperAdminsPageComponent
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
    component: StartPageComponent
  },
  {
    path: 'callback',
    component: CallbackPageComponent,
  },
  {
    path: '**',
    redirectTo: '',
  },
];
