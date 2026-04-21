import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { vi } from 'vitest';

import { StartPageComponent } from './start-page.component';

describe('Startpage', () => {
  let component: StartPageComponent;
  let fixture: ComponentFixture<StartPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StartPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {},
        },
        {
          provide: OAuthService,
          useValue: { hasValidAccessToken: vi.fn(() => false) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StartPageComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
