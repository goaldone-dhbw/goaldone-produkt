import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Button } from 'primeng/button';
import { TestService, UserInfoResponse } from '../../api';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-test-page',
  standalone: true,
  imports: [CommonModule, Button],
  templateUrl: './test-page.component.html',
})
export class TestPageComponent implements OnInit {
  private testService = inject(TestService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  loading = false;
  error: string | null = null;
  userInfo: UserInfoResponse | null = null;
  tokenRoles: string[] = [];
  tokenOrgId: string | null = null;

  ngOnInit(): void {
    this.fetchUserInfo();
    this.tokenRoles = this.authService.getUserRoles();
    this.tokenOrgId = this.authService.getUserOrganizationId();
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
