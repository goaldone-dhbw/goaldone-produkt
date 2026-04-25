import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Button } from 'primeng/button';
import { UserAccountsService, AccountResponse } from '../../api';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-user-settings',
  standalone: true,
  imports: [CommonModule, Button],
  templateUrl: './user-settings.page.html',
})
export class UserSettingsPage implements OnInit {
  private userAccountsService = inject(UserAccountsService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  loading = false;
  error: string | null = null;
  accounts: AccountResponse[] = [];

  ngOnInit(): void {
    this.fetchAccounts();
  }

  private fetchAccounts(): void {
    this.loading = true;
    this.error = null;
    this.userAccountsService.getMyAccounts().subscribe({
      next: (data) => {
        this.accounts = data.accounts ?? [];
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = err.message || 'Failed to fetch accounts';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
