import { Component, computed, inject, model } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { AuthService } from '../../../core/auth/auth.service';
import { Drawer } from 'primeng/drawer';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  templateUrl: './app-sidebar.component.html',
  styleUrl: './app-sidebar.component.scss',
  imports: [Menu, Drawer],
})
export class AppSidebarComponent {
  private authService = inject(AuthService);

  visible = model<boolean>(false);

  protected menuItems = computed<MenuItem[]>(() => {
    const roles = this.authService.getUserRoles();
    const closeSidebar = () => this.visible.set(false);

    const workspaceItems: MenuItem[] = [
      {
        label: 'Workspace',
        icon: 'pi pi-home',
        routerLink: '/app',
        command: closeSidebar,
      },
      {
        label: 'Aufgaben & Routinen',
        icon: 'pi pi-list',
        routerLink: '/app/tasks',
        command: closeSidebar,
      },
      {
        label: 'Planungsansicht',
        icon: 'pi pi-calendar-clock',
        routerLink: '/app/schedule',
        command: closeSidebar,
      },
      {
        label: 'Arbeitszeiten & Pausen',
        icon: 'pi pi-clock',
        routerLink: '/app/working-hours',
        command: closeSidebar,
      },
    ];

    const settingsItems: MenuItem[] = [
      {
        label: 'Einstellungen',
        icon: 'pi pi-cog',
        routerLink: '/app/settings',
        command: closeSidebar,
      },
    ];

    // Add Admin pages if applicable
    if (roles.includes('COMPANY_ADMIN')) {
      settingsItems.unshift({
        label: 'Organisation verwalten',
        icon: 'pi pi-building',
        routerLink: '/app/organization',
        command: closeSidebar,
      });
    }

    if (roles.includes('SUPER_ADMIN')) {
      settingsItems.unshift({
        label: 'Super-Admin',
        icon: 'pi pi-key',
        routerLink: '/app/super-admin',
        command: closeSidebar,
      });
    }

    return [
      {
        label: 'Menü',
        items: workspaceItems,
      },
      {
        label: 'Verwaltung',
        items: settingsItems,
      },
    ];
  });
}
