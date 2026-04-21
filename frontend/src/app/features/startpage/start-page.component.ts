import { Component } from '@angular/core';
import { Button } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { BasePopupComponent } from '../../shared/base-popup/base-popup.component';

@Component({
  selector: 'app-startpage',
  standalone: true,
  imports: [Button, RouterLink, BasePopupComponent],
  templateUrl: './start-page.component.html',
})
export class StartPageComponent {
  isEmptyPopupOpen = false;
  isContentPopupOpen = false;

  showEmptyBasePopup(): void {
    this.isEmptyPopupOpen = true;
  }

  showBasePopupWithContent(): void {
    this.isContentPopupOpen = true;
  }

  closeEmptyBasePopup(): void {
    this.isEmptyPopupOpen = false;
  }

  closeBasePopupWithContent(): void {
    this.isContentPopupOpen = false;
  }
}
