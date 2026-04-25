import { Component, computed, inject, model } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { Drawer } from 'primeng/drawer';
import { AccountStore } from '../../../core/accounts/account.store';
import { UserAccountsService } from '../../../api';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  templateUrl: './app-sidebar.component.html',
  styleUrl: './app-sidebar.component.scss',
  imports: [Menu, Drawer],
})
export class AppSidebarComponent {
  private accountStore = inject(AccountStore);
  private userAccountsService = inject(UserAccountsService);

  constructor() {
    this.userAccountsService.getMyAccounts().subscribe({
      next: (response) => {
        console.log('Accounts loaded:', response.accounts);
        this.accountStore.setAccounts(response.accounts);
      },
      error: (err) => {
        console.error('Error loading accounts:', err);
      },
    });
  }

  visible = model<boolean>(false);

  protected menuItems = computed<MenuItem[]>(() => {
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
    if (this.accountStore.hasCompanyAdminRole()) {
      settingsItems.unshift({
        label: 'Organisation verwalten',
        icon: 'pi pi-building',
        routerLink: '/app/organization',
        command: closeSidebar,
      });
    }

    if (this.accountStore.hasSuperAdminRole()) {
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
