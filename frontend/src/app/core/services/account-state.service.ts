import { Injectable, signal, inject } from '@angular/core';
import { UserAccountsService } from '../../api';
import { TenantService } from './tenant.service';

@Injectable({
  providedIn: 'root'
})
export class AccountStateService {
  private userAccountsService = inject(UserAccountsService);
  private tenantService = inject(TenantService);

  hasConflicts = signal(false);

  refresh(): void {
    const orgId = this.tenantService.getActiveOrgId() || '';
    this.userAccountsService.getMyAccounts(orgId).subscribe({
      next: (response) => {
        const conflicts = response.accounts?.some(acc => acc.hasConflicts) ?? false;
        this.hasConflicts.set(conflicts);
      },
      error: (error) => {
        console.error('Failed to refresh account state:', error);
      }
    });
  }
}
