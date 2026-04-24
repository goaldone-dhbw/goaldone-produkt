import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppSidebarComponent } from './app-sidebar.component';
import { AuthService } from '../../../core/auth/auth.service';
import { provideRouter } from '@angular/router';

describe('AppSidebarComponent', () => {
  let component: AppSidebarComponent;
  let fixture: ComponentFixture<AppSidebarComponent>;
  let authServiceMock: any;

  beforeEach(async () => {
    authServiceMock = {
      getUserRoles: vi.fn().mockReturnValue(['USER']),
    };

    await TestBed.configureTestingModule({
      imports: [AppSidebarComponent],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
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
