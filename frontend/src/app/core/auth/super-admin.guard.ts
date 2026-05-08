import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AccountStore } from '../accounts/account.store';

/**
 * Guard that protects routes requiring SUPER_ADMIN role.
 * Ensures accounts are loaded from /users/accounts API before checking authorization.
 * Redirects unauthorized users to /app (home).
 */
export const superAdminGuard: CanActivateFn = () => {
  const accountStore = inject(AccountStore);
  const router = inject(Router);

  return accountStore.ensureLoaded().pipe(
    map(() => accountStore.hasSuperAdminRole() || router.createUrlTree(['/app'])),
  );
};
