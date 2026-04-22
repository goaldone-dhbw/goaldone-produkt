import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-callback',
  imports: [CardModule],
  template: `
    <div class="flex flex-col items-center justify-center h-screen gap-4">
      <p-card>
        <ng-template pTemplate="header">
          <p class="text-xl font-semibold">Login erfolgreich!</p>
        </ng-template>
        <p>Du wirst weitergeleitet...</p>
      </p-card>
    </div>
  `,
})
export class CallbackPageComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    // Token exchange should already be complete from APP_INITIALIZER,
    // but ensure it's done, then redirect to /test
    if (this.authService.hasValidAccessToken()) {
      setTimeout(() => {
        this.router.navigateByUrl('/test');
      }, 800);
    }
  }
}
