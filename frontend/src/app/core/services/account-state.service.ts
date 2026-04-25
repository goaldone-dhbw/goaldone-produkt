import { Injectable, signal } from '@angular/core';
import { UserAccountsService } from '../../api';

@Injectable({
  providedIn: 'root'
})
export class AccountStateService {
  hasConflicts = signal(false);

  constructor(private userAccountsService: UserAccountsService) {}

  refresh(): void {
    this.userAccountsService.getMyAccounts().subscribe({
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
