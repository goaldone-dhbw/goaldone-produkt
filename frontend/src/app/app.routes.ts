import { Routes } from '@angular/router';
import { TestPageComponent } from './features/test-page/test-page.component';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'test',
    component: TestPageComponent,
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'test', pathMatch: 'full' },
];
