import { Component, OnInit, inject, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { AuthService } from '../../core/auth/auth.service';
import { CommonModule } from '@angular/common';
import { OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-callback',
  imports: [CardModule, CommonModule],
  templateUrl: './callback-page.component.html',
})
export class CallbackPageComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private oauthService = inject(OAuthService);
  private router = inject(Router);
  private destroy$ = new Subject<void>();

  isLoading = true;
  hasError = false;
  errorMessage = '';

  ngOnInit(): void {
    console.log('[CallbackPage] Component initialized');
    console.log('[CallbackPage] Current URL before cleanup:', window.location.href);
    console.log('[CallbackPage] Current query string:', window.location.search);

    // If we have an authorization code in the URL, we need to perform the token exchange
    // This handles the case where the user is redirected to /callback after login
    // but APP_INITIALIZER has already run (no code was detected the first time)
    if (window.location.search.includes('code=')) {
      console.log('[CallbackPage] Authorization code detected in URL, manually triggering tryLogin...');
      this.oauthService.tryLoginCodeFlow().then(() => {
        console.log('[CallbackPage] Manual tryLoginCodeFlow completed');
      }).catch((error) => {
        console.error('[CallbackPage] Manual tryLoginCodeFlow failed:', error);
      });
    }

    // Remove any URL fragments/parameters to prevent infinite loops
    // The token has already been processed by APP_INITIALIZER or manual tryLogin above
    window.history.replaceState({}, document.title, window.location.pathname);
    console.log('[CallbackPage] URL parameters cleaned');

    // Wait a moment for manual tryLogin to complete if needed, then check if token exchange succeeded
    setTimeout(() => {
      const hasToken = this.authService.hasValidAccessToken();
      console.log('[CallbackPage] hasValidAccessToken() after initial wait:', hasToken);

      if (hasToken) {
        // Token is valid, redirect to app after a short delay
        this.isLoading = false;
        console.log('[CallbackPage] Token is valid, scheduling redirect to /app');
        setTimeout(() => {
          console.log('[CallbackPage] Token valid, navigating to /app');
          this.router.navigateByUrl('/app');
        }, 500);
      } else {
        // No valid token - wait a moment for async token processing to complete
        console.log('[CallbackPage] No token yet, waiting for async processing...');
        console.log('[CallbackPage] Subscribing to OAuthService events...');

        // Subscribe to token events from OAuthService
        this.oauthService.events
          .pipe(takeUntil(this.destroy$))
          .subscribe((event) => {
            console.log('[CallbackPage] OAuth event received:', event.type, event);

            if (event.type === 'token_received' || event.type === 'token_refreshed') {
              // Token was received successfully
              console.log('[CallbackPage] Token received event, checking token validity...');
              console.log('[CallbackPage] hasValidAccessToken() after event:', this.authService.hasValidAccessToken());
              this.isLoading = false;
              setTimeout(() => {
                console.log('[CallbackPage] Token received, navigating to /app');
                this.router.navigateByUrl('/app');
              }, 300);
            } else if (event.type === 'code_error' || event.type === 'token_error') {
              // Token exchange failed
              this.isLoading = false;
              this.hasError = true;
              this.errorMessage = 'Authentication failed. Please try logging in again.';
              console.error('[CallbackPage] Token exchange error event:', event.type, event);

              setTimeout(() => {
                console.log('[CallbackPage] Redirecting to / due to error');
                this.router.navigateByUrl('/');
              }, 3000);
            }
          });

        // Timeout: if no token after 3 seconds, show error
        setTimeout(() => {
          const stillNoToken = !this.authService.hasValidAccessToken();
          console.log('[CallbackPage] 3-second timeout check. Token valid?', !stillNoToken, 'Has error?', this.hasError);

          if (stillNoToken && !this.hasError) {
            this.isLoading = false;
            this.hasError = true;
            this.errorMessage = 'Authentication timeout. Please try logging in again.';
            console.error('[CallbackPage] Token exchange timeout - no token received after 3 seconds');

            setTimeout(() => {
              console.log('[CallbackPage] Redirecting to / due to timeout');
              this.router.navigateByUrl('/');
            }, 2000);
          }
        }, 3000);
      }
    }, 500);  // Wait 500ms for async operations to complete
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
