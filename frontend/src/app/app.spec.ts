import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { OAuthService } from 'angular-oauth2-oidc';
import { EMPTY } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

const oauthServiceMock = {
  events: EMPTY,
  configure: () => {},
  loadDiscoveryDocumentAndTryLogin: () => Promise.resolve(true),
  hasValidAccessToken: () => false,
  getAccessToken: () => '',
  getRefreshToken: () => '',
  getIdToken: () => '',
  logOut: () => {},
  initLoginFlow: () => {},
  refreshToken: () => Promise.resolve({}),
};

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: OAuthService, useValue: oauthServiceMock },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

});
