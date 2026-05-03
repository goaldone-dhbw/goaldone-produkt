import { Component, inject, OnInit } from '@angular/core';
import { Button } from 'primeng/button';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-startpage',
  standalone: true,
  imports: [Button],
  templateUrl: './start-page.component.html',
})
export class StartPageComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    console.log('[StartPage] Component initialized, URL:', window.location.href);
  }

  get isLoggedIn(): boolean {
    const result = this.authService.hasValidAccessToken();
    console.log('[StartPage.isLoggedIn getter] called, result:', result);
    return result;
  }

  handleAction(): void {
    if (this.isLoggedIn) {
      this.router.navigate(['/app']);
    } else {
      this.authService.initLoginFlow();
    }
  }
}
