import { Routes } from '@angular/router';
import { TestPageComponent } from './features/test-page/test-page.component';
import { authGuard } from './core/auth/auth.guard';
import { StartPageComponent } from './features/startpage/start-page.component';

export const routes: Routes = [
  {
    path: 'test',
    component: TestPageComponent,
    canActivate: [authGuard],
  },
  {
    path: '',
    component: StartPageComponent,
  }
];
