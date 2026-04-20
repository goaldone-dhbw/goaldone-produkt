import { Component, inject } from '@angular/core';
import { Button } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-startpage',
  imports: [Button, RouterLink, CommonModule],
  templateUrl: './start-page.component.html',
})
export class StartPageComponent {
  private authService = inject(AuthService);

  isLoggedIn = () => this.authService.hasValidAccessToken();

  login(): void {
    this.authService.initLoginFlow();
  }
}
