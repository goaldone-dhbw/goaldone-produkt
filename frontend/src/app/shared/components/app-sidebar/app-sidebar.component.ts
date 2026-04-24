import { Component, computed, inject, model } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { Drawer } from 'primeng/drawer';
import { AuthService } from '../../../core/auth/auth.service';

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

  protected navigationItems = computed<MenuItem[]>(() => {
    const roles = this.authService.getUserRoles();
    if (roles.includes('SUPER_ADMIN')) {
      return [];
    }

    return [
      {
        label: 'Workspace',
        icon: 'pi pi-home',
        routerLink: '/app',
      },
      {
        label: 'Aufgaben & Routinen',
        icon: 'pi pi-list',
        routerLink: '/app/tasks',
      },
      {
        label: 'Planungsansicht',
        icon: 'pi pi-calendar-clock',
        routerLink: '/app/schedule',
      },
      {
        label: 'Arbeitszeiten & Pausen',
        icon: 'pi pi-clock',
        routerLink: '/app/working-hours',
      },
    ];
  });

  protected settingsItems = computed<MenuItem[]>(() => {
    const roles = this.authService.getUserRoles();
    const items: MenuItem[] = [
      {
        label: 'Einstellungen',
        icon: 'pi pi-cog',
        routerLink: '/app/settings',
      },
    ];

    // Nur für ADMIN
    if (roles.includes('COMPANY_ADMIN')) {
      items.unshift({
        label: 'Organisation verwalten',
        icon: 'pi pi-building',
        routerLink: '/app/organization',
      });
    }

    // Nur SuperAdmin
    if (roles.includes('SUPER_ADMIN')) {
      items.unshift({
        label: 'Super-Admin',
        icon: 'pi pi-key',
        routerLink: '/app/super-admin',
      });
    }

    return items;
  });
}
