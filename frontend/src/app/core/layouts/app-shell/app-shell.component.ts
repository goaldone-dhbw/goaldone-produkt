import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { AppSidebarComponent } from '../../../shared/components/app-sidebar/app-sidebar.component';
import { Image } from 'primeng/image';
import { MessageModule } from 'primeng/message';
import { AccountStateService } from '../../services/account-state.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, AppSidebarComponent, Button, Image, RouterLink, MessageModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  sidebarVisible = signal(false);

  constructor(protected accountStateService: AccountStateService) {}

  toggleSidebar() {
    this.sidebarVisible.update((visible) => !visible);
  }
}
