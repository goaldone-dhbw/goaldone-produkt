// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppSidebarComponent } from './app-sidebar.component';
import { AuthService } from '../../../core/auth/auth.service';
import { OrgContextService } from '../../../core/services/org-context.service';
import { provideRouter } from '@angular/router';
import { AccountStore } from '../../../core/accounts/account.store';
import { UserAccountsService } from '../../../api';
import { of } from 'rxjs';

describe('AppSidebarComponent', () => {
  let component: AppSidebarComponent;
  let fixture: ComponentFixture<AppSidebarComponent>;
  let authServiceMock: any;
  let accountStoreMock: any;
  let userAccountsServiceMock: any;

  beforeEach(async () => {
    authServiceMock = {
      logout: vi.fn(),
    };

    accountStoreMock = {
      hasCompanyAdminRole: vi.fn().mockReturnValue(false),
      hasSuperAdminRole: vi.fn().mockReturnValue(false),
      setAccounts: vi.fn(),
    };

    userAccountsServiceMock = {
      getMyAccounts: vi.fn().mockReturnValue(of({ accounts: [] })),
    };

    const orgContextServiceMock = {
      getDefaultOrg: vi.fn().mockReturnValue({ id: 'org-1', slug: 'test-org', role: 'USER' }),
      getDialogOrg: vi.fn().mockReturnValue(null),
      getSettingsOrg: vi.fn().mockReturnValue(null),
    };

    await TestBed.configureTestingModule({
      imports: [AppSidebarComponent],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: AccountStore, useValue: accountStoreMock },
        { provide: UserAccountsService, useValue: userAccountsServiceMock },
        { provide: OrgContextService, useValue: orgContextServiceMock },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppSidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call authService.logout when logout is called', () => {
    component.logout();
    expect(authServiceMock.logout).toHaveBeenCalled();
  });

  it('should close sidebar when a menu item command is executed', () => {
    component.visible.set(true);
    expect(component.visible()).toBe(true);

    const menuItems = (component as any).menuItems();
    const firstItem = menuItems[0].items[0];

    expect(firstItem.command).toBeDefined();
    firstItem.command();

    expect(component.visible()).toBe(false);
  });
});
