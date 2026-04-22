import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TestService, UserInfoResponse } from '../../api';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-user-settings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './user-settings.page.html',
})
export class UserSettingsPage implements OnInit {
  private testService = inject(TestService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  loading = false;
  error: string | null = null;
  userInfo: UserInfoResponse | null = null;

  ngOnInit(): void {
    this.fetchUserInfo();
  }

  private fetchUserInfo(): void {
    this.loading = true;
    this.error = null;
    this.testService.getCurrentUserInfo().subscribe({
      next: (data) => {
        this.userInfo = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = err.message || 'Failed to fetch user info';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
