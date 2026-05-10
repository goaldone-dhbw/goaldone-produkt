import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';

import { AuthService } from '../../core/auth/auth.service';
import { AccountLinkingStorageService } from '../../core/services/account-linking-storage.service';

@Component({
  selector: 'app-callback',
  imports: [CardModule],
  templateUrl: './callback-page.component.html',
})
export class CallbackPageComponent implements OnInit {
  private authService = inject(AuthService);
  private accountLinkingStorage = inject(AccountLinkingStorageService);
  private router = inject(Router);

  ngOnInit(): void {
    if (!this.authService.hasValidAccessToken()) {
      return;
    }

    setTimeout(() => {
      if (this.accountLinkingStorage.hasPendingLink()) {
        this.router.navigateByUrl('/link-callback');
        return;
      }

      this.router.navigateByUrl('/app');
    }, 800);
  }
}
