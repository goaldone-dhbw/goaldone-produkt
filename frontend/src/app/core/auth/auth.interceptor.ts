import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  // Read API base path at runtime to support dynamic env.js injection
  // Default to localhost:8080 for local dev if not set
  const apiBasePath =
    (window as any).__env?.['apiBasePath'] || 'http://localhost:8080';

  if (token && req.url.startsWith(apiBasePath)) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }
  return next(req);
};
