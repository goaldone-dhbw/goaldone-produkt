import { Component, inject } from '@angular/core';
import { Button } from 'primeng/button';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-startpage',
  standalone: true,
  imports: [Button],
  templateUrl: './start-page.component.html',
})
export class StartPageComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  get isLoggedIn(): boolean {
    return this.authService.hasValidAccessToken();
  }

  handleAction(): void {
    if (this.isLoggedIn) {
      this.router.navigate(['/app']);
    } else {
      this.authService.initLoginFlow();
    }
  }
}
