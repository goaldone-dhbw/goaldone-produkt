import { Component, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AccountStateService } from './core/services/account-state.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly title = signal('frontend');

  constructor(protected accountStateService: AccountStateService) {}

  ngOnInit(): void {
    this.accountStateService.refresh();
  }
}
