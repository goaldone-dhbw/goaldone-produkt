import { Injectable, signal, computed } from '@angular/core';


export interface Account {
  accountId: string;
  organizationId: string;
  organizationName: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AccountStore {
  readonly accounts = signal<Account[]>([]);

  readonly hasCompanyAdminRole = computed(() =>
    this.accounts().some((account) => account.roles.includes('COMPANY_ADMIN')),
  );

  readonly hasSuperAdminRole = computed(() =>
    this.accounts().some((account) => account.roles.includes('SUPER_ADMIN')),
  );

  setAccounts(accounts: Account[]): void {
    this.accounts.set(accounts);
  }


  clear(): void {
    this.accounts.set([]);
  }
}
