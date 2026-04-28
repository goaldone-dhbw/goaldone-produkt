import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';


@Component({
  selector: 'app-org-settings',
  standalone: true,
  imports: [FormsModule, Card, Button, InputText, Message],
  templateUrl: './org-settings.page.html',
  styleUrl: './org-settings.page.scss',
})
export class OrgSettingsPage {
  inviteEmail = signal('');
  inviteError = signal<string | null>(null);
  inviteWarn = signal<string | null>(null);
  inviteSending = signal(false);

  isInviteEmailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.inviteEmail()));

  sendInvitation() {
    if (this.inviteSending()) return;
    this.inviteError.set(null);
    this.inviteWarn.set(null);

    if (!this.isInviteEmailValid()) {
      this.inviteError.set('Bitte geben Sie eine gültige E-Mail-Adresse ein.');
      return;
    }

    this.inviteSending.set(true);

    setTimeout(() => {
      this.inviteSending.set(false);
      this.inviteEmail.set('');
      this.inviteWarn.set(
        'Einladung kann erst gesendet werden, sobald der Backend-Endpunkt verfügbar ist.',
      );
    }, 1000);
  }
}
