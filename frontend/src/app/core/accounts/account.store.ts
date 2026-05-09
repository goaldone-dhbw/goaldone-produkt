import { Injectable, signal, computed } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { UserAccountsService } from '../../api/api/userAccounts.service';
import { AccountListResponse } from '../../api/model/accountListResponse';


export interface Account {
  accountId: string;
  organizationId: string;
  organizationName: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AccountStore {
  readonly accounts = signal<Account[]>([]);
  private readonly loaded = signal(false);

  readonly hasCompanyAdminRole = computed(() =>
    this.accounts().some((account) => account.roles.includes('COMPANY_ADMIN')),
  );

  readonly hasSuperAdminRole = computed(() =>
    this.accounts().some((account) => account.roles.includes('SUPER_ADMIN')),
  );

  constructor(private userAccountsService: UserAccountsService) {}

  setAccounts(accounts: Account[]): void {
    this.accounts.set(accounts);
  }

  /**
   * Ensures accounts are loaded from the API.
   * If already loaded, returns an observable that completes immediately.
   * Otherwise, fetches accounts from /users/accounts and updates the store.
   */
  ensureLoaded(): Observable<void> {
    if (this.loaded()) {
      return of(void 0);
    }

    return this.userAccountsService.getMyAccounts().pipe(
      map((response: AccountListResponse) => response.accounts),
      tap((accounts: Account[]) => {
        this.setAccounts(accounts);
        this.loaded.set(true);
      }),
      map(() => void 0),
    );
  }

  clear(): void {
    this.accounts.set([]);
  }
}
