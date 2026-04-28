import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { OrgSettingsPage } from './org-settings.page';
import { MemberManagementService, MemberRole } from '../../api';
import { AccountStore } from '../../core/accounts/account.store';

describe('OrgSettingsPage', () => {
  let component: OrgSettingsPage;
  let fixture: ComponentFixture<OrgSettingsPage>;

  const memberManagementServiceMock = {
    inviteMember: vi.fn(),
  };

  beforeEach(async () => {
    memberManagementServiceMock.inviteMember.mockReset();

    await TestBed.configureTestingModule({
      imports: [OrgSettingsPage],
      providers: [
        {
          provide: MemberManagementService,
          useValue: memberManagementServiceMock,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrgSettingsPage);
    component = fixture.componentInstance;

    const accountStore = TestBed.inject(AccountStore);
    accountStore.setAccounts([
      {
        accountId: 'account-1',
        organizationId: 'org-1',
        organizationName: 'Test Organisation',
        roles: ['COMPANY_ADMIN'],
      },
    ]);

    component.inviteFirstName.set('Max');
    component.inviteLastName.set('Mustermann');

    fixture.detectChanges();
  });

  it('should accept a valid email address', () => {
    component.inviteEmail.set('neuer.nutzer@test.de');

    expect(component.isInviteEmailValid()).toBe(true);
    expect(component.inviteError()).toBeNull();
    expect(component.isInviteFormValid()).toBe(true);
  });

  it('should send invitation successfully, show success message and clear inputs', () => {
    memberManagementServiceMock.inviteMember.mockReturnValue(of(null));

    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('Max');
    component.inviteLastName.set('Mustermann');

    component.sendInvitation();

    expect(memberManagementServiceMock.inviteMember).toHaveBeenCalledTimes(1);
    expect(memberManagementServiceMock.inviteMember).toHaveBeenCalledWith('org-1', {
      email: 'neuer.nutzer@test.de',
      firstName: 'Max',
      lastName: 'Mustermann',
      role: MemberRole.User,
    });

    expect(component.inviteSuccess()).toBe('Einladung wurde erfolgreich versendet.');
    expect(component.inviteError()).toBeNull();

    expect(component.inviteEmail()).toBe('');
    expect(component.inviteFirstName()).toBe('');
    expect(component.inviteLastName()).toBe('');
  });

  it('should show loading state during API request and prevent duplicate submit', () => {
    const request$ = new Subject<unknown>();
    memberManagementServiceMock.inviteMember.mockReturnValue(request$);

    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('Max');
    component.inviteLastName.set('Mustermann');

    component.sendInvitation();

    expect(component.inviteSending()).toBe(true);
    expect(memberManagementServiceMock.inviteMember).toHaveBeenCalledTimes(1);

    component.sendInvitation();

    expect(memberManagementServiceMock.inviteMember).toHaveBeenCalledTimes(1);

    request$.next(null);
    request$.complete();

    expect(component.inviteSending()).toBe(false);
    expect(component.inviteSuccess()).toBe('Einladung wurde erfolgreich versendet.');
  });

  it('should reject an invalid email address and not call API', () => {
    component.inviteEmail.set('abc');

    component.sendInvitation();

    expect(component.isInviteEmailValid()).toBe(false);
    expect(component.inviteError()).toBe('Bitte geben Sie eine gültige E-Mail-Adresse ein.');
    expect(memberManagementServiceMock.inviteMember).not.toHaveBeenCalled();
  });

  it('should reject an empty email address and not call API', () => {
    component.inviteEmail.set('');

    component.sendInvitation();

    expect(component.isInviteEmailValid()).toBe(false);
    expect(component.inviteError()).toBe('Bitte geben Sie eine gültige E-Mail-Adresse ein.');
    expect(memberManagementServiceMock.inviteMember).not.toHaveBeenCalled();
  });

  it('should show error message on API error and keep inputs', () => {
    memberManagementServiceMock.inviteMember.mockReturnValue(throwError(() => ({ status: 500 })));

    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('Max');
    component.inviteLastName.set('Mustermann');

    component.sendInvitation();

    expect(memberManagementServiceMock.inviteMember).toHaveBeenCalledTimes(1);
    expect(component.inviteError()).toBe('Einladung konnte nicht gesendet werden.');
    expect(component.inviteSuccess()).toBeNull();

    expect(component.inviteEmail()).toBe('neuer.nutzer@test.de');
    expect(component.inviteFirstName()).toBe('Max');
    expect(component.inviteLastName()).toBe('Mustermann');
  });

  it('should show conflict message when email is already invited', () => {
    memberManagementServiceMock.inviteMember.mockReturnValue(throwError(() => ({ status: 409 })));

    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('Max');
    component.inviteLastName.set('Mustermann');

    component.sendInvitation();

    expect(memberManagementServiceMock.inviteMember).toHaveBeenCalledTimes(1);
    expect(component.inviteError()).toBe('Für diese E-Mail existiert bereits eine Einladung.');
    expect(component.inviteSuccess()).toBeNull();

    expect(component.inviteEmail()).toBe('neuer.nutzer@test.de');
  });

  it('should reject empty first name and not call API', () => {
    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('');
    component.inviteLastName.set('Mustermann');

    component.sendInvitation();

    expect(component.inviteError()).toBe(
      'Vorname und Nachname dürfen nicht leer sein und maximal 200 Zeichen enthalten.',
    );
    expect(memberManagementServiceMock.inviteMember).not.toHaveBeenCalled();
  });

  it('should reject first name with more than 200 characters and not call API', () => {
    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('a'.repeat(201));
    component.inviteLastName.set('Mustermann');

    component.sendInvitation();

    expect(component.inviteError()).toBe(
      'Vorname und Nachname dürfen nicht leer sein und maximal 200 Zeichen enthalten.',
    );
    expect(memberManagementServiceMock.inviteMember).not.toHaveBeenCalled();
  });

  it('should reject empty last name and not call API', () => {
    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('Max');
    component.inviteLastName.set('');

    component.sendInvitation();

    expect(component.inviteError()).toBe(
      'Vorname und Nachname dürfen nicht leer sein und maximal 200 Zeichen enthalten.',
    );
    expect(memberManagementServiceMock.inviteMember).not.toHaveBeenCalled();
  });

  it('should reject last name with more than 200 characters and not call API', () => {
    component.inviteEmail.set('neuer.nutzer@test.de');
    component.inviteFirstName.set('Max');
    component.inviteLastName.set('a'.repeat(201));

    component.sendInvitation();

    expect(component.inviteError()).toBe(
      'Vorname und Nachname dürfen nicht leer sein und maximal 200 Zeichen enthalten.',
    );
    expect(memberManagementServiceMock.inviteMember).not.toHaveBeenCalled();
  });
});
