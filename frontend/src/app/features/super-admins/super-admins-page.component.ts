import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { AddSuperAdminDialogComponent } from './add-super-admin-dialog.component';


@Component({
  selector: 'app-super-admins-page',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    AddSuperAdminDialogComponent,
  ],
  templateUrl: './super-admins-page.component.html',
})
export class SuperAdminsPageComponent {
  addDialogVisible = false;

  openAddDialog(): void {
    this.addDialogVisible = true;
  }

  onDialogVisibleChange(value: boolean): void {
    this.addDialogVisible = value;
  }

  onAdded(): void {
    console.log('Super-Admin wurde hinzugefügt oder Einladung verschickt');
    this.addDialogVisible = false;
  }
}
