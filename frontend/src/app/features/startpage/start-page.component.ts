import { Component } from '@angular/core';
import { Button } from 'primeng/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-startpage',
  imports: [Button, RouterLink],
  templateUrl: './start-page.component.html',
})
export class StartPageComponent {}
