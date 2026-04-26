import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { Button } from 'primeng/button';
import { PrimeTemplate } from 'primeng/api';

@Component({
  selector: 'app-base-popup',
  standalone: true,
  imports: [Dialog, Button, PrimeTemplate],
  templateUrl: './base-popup.component.html',
})
export class BasePopupComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() title = 'Hinweis';
  @Input() content = '';

  @Input() confirmLabel = 'Ok';
  @Input() cancelLabel = 'Abbrechen';

  @Input() showConfirmButton = true;
  @Input() showCancelButton = true;
  @Input() autoCloseOnConfirm = true;

  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();
  @Output() isOpenChange = new EventEmitter<boolean>();

  dialogVisible = false;

  ngOnChanges(changes: SimpleChanges): void {
    if ('isOpen' in changes) {
      this.dialogVisible = this.isOpen;
    }
  }

  onConfirm(): void {
    this.confirm.emit();
    if (this.autoCloseOnConfirm) {
      this.dialogVisible = false;
      this.isOpenChange.emit(false);
    }
  }

  onCancel(): void {
    this.cancel.emit();
    this.dialogVisible = false;
    this.isOpenChange.emit(false);
  }

  onClose(): void {
    this.dialogVisible = false;
    this.isOpenChange.emit(false);
    this.closed.emit();
  }
}
