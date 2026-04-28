import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrgSettingsPage } from './org-settings.page';
import { describe, it, expect, beforeEach } from 'vitest';

describe('OrgSettingsPage', () => {
  let fixture: ComponentFixture<OrgSettingsPage>;
  let component: OrgSettingsPage;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrgSettingsPage],
    }).compileComponents();

    fixture = TestBed.createComponent(OrgSettingsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should accept valid email address', () => {
    component.inviteEmail.set('neuer.nutzer@test.de');

    expect(component.isInviteEmailValid()).toBe(true);
    expect(component.inviteError()).toBeNull();
  });

  it('should reject invalid email address', () => {
    component.inviteEmail.set('abc');

    component.sendInvitation();

    expect(component.isInviteEmailValid()).toBe(false);
    expect(component.inviteError()).toBe('Bitte geben Sie eine gültige E-Mail-Adresse ein.');
  });

  it('should reject empty email address', () => {
    component.inviteEmail.set('');

    component.sendInvitation();

    expect(component.isInviteEmailValid()).toBe(false);
    expect(component.inviteError()).toBe('Bitte geben Sie eine gültige E-Mail-Adresse ein.');
  });

  it.skip('should send invitation successfully and clear input', () => {
    // Blockiert: Der Backend-Endpunkt bzw. generierte OpenAPI-Service für POST /invitations existiert noch nicht.
  });

  it.skip('should show loading state while API request is running', () => {
    // Blockiert: Der Backend-Endpunkt bzw. generierte OpenAPI-Service für POST /invitations existiert noch nicht.
  });

  it.skip('should show error when API returns 500 and keep input', () => {
    // Blockiert: Der Backend-Endpunkt bzw. generierte OpenAPI-Service für POST /invitations existiert noch nicht.
  });

  it.skip('should show error when invitation already exists', () => {
    // Blockiert: Der Backend-Endpunkt bzw. generierte OpenAPI-Service für POST /invitations existiert noch nicht.
  });
});
