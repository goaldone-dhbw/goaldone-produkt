import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { AppSidebarComponent } from '../../../shared/components/app-sidebar/app-sidebar.component';
import { Image } from 'primeng/image';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, AppSidebarComponent, Button, Image, RouterLink],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  sidebarVisible = signal(false);

  toggleSidebar() {
    this.sidebarVisible.update((visible) => !visible);
  }
}
