import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-callback',
  imports: [CardModule],
  templateUrl: './callback-page.component.html',
})
export class CallbackPageComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    // Token exchange should already be complete from APP_INITIALIZER,
    // but ensure it's done, then redirect to /test
    if (this.authService.hasValidAccessToken()) {
      setTimeout(() => {
        this.router.navigateByUrl('/app');
      }, 800);
    }
  }
}
