import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Tooltip } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import {
  WorkingTimesService,
  UserAccountsService,
  WorkingTimeResponse,
  AccountResponse,
  WorkingTimeCreateRequest,
  WorkingTimeUpdateRequest,
  DayOfWeek
} from '../../api';
import { AccountStateService } from '../../core/services/account-state.service';

@Component({
  selector: 'app-working-hours',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    ConfirmDialogModule,
    TagModule,
    ToastModule,
    SelectButtonModule,
    Tooltip
  ],
  templateUrl: './working-hours.page.html',
  providers: [ConfirmationService, MessageService]
})
export class WorkingHoursPage implements OnInit {
  workingTimes = signal<WorkingTimeResponse[]>([]);
  accounts = signal<AccountResponse[]>([]);
  loading = signal(false);
  showDialog = signal(false);
  isEditMode = signal(false);

  selectedDays: DayOfWeek[] = [];
  selectedAccountId: string | null = null;
  editingId: string | null = null;
  startTimeString: string = '';
  endTimeString: string = '';

  readonly dayOptions = [
    { label: 'Mo', value: DayOfWeek.Monday },
    { label: 'Di', value: DayOfWeek.Tuesday },
    { label: 'Mi', value: DayOfWeek.Wednesday },
    { label: 'Do', value: DayOfWeek.Thursday },
    { label: 'Fr', value: DayOfWeek.Friday },
    { label: 'Sa', value: DayOfWeek.Saturday },
    { label: 'So', value: DayOfWeek.Sunday },
  ];

  constructor(
    private workingTimesService: WorkingTimesService,
    private userAccountsService: UserAccountsService,
    private accountStateService: AccountStateService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService
  ) {}

  ngOnInit(): void {
    this.loadAccounts();
    this.loadWorkingTimes();
  }

  loadAccounts(): void {
    this.userAccountsService.getMyAccounts().subscribe({
      next: (response) => {
        this.accounts.set(response.accounts || []);
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Accounts konnten nicht geladen werden.'
        });
      }
    });
  }

  loadWorkingTimes(): void {
    this.loading.set(true);
    this.workingTimesService.getWorkingTimes().subscribe({
      next: (response) => {
        this.workingTimes.set(response.items || []);
        this.loading.set(false);
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: 'Arbeitszeiten konnten nicht geladen werden.'
        });
        this.loading.set(false);
      }
    });
  }

  openCreateDialog(): void {
    this.isEditMode.set(false);
    this.editingId = null;
    this.selectedDays = [];
    this.startTimeString = '08:00';
    this.endTimeString = '17:00';
    this.selectedAccountId = this.accounts().length > 0 ? this.accounts()[0].accountId?.toString() || null : null;
    this.showDialog.set(true);
  }

  openEditDialog(item: WorkingTimeResponse): void {
    this.isEditMode.set(true);
    this.editingId = item.id?.toString() || null;
    this.selectedAccountId = item.accountId?.toString() || null;
    this.selectedDays = item.days ? [...item.days] : [];
    this.startTimeString = item.startTime || '08:00';
    this.endTimeString = item.endTime || '17:00';
    this.showDialog.set(true);
  }

  saveWorkingTime(): void {
    if (!this.selectedAccountId) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validierung',
        detail: 'Bitte wählen Sie eine Organisation aus.'
      });
      return;
    }

    if (!this.startTimeString) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validierung',
        detail: 'Bitte geben Sie eine Startzeit an.'
      });
      return;
    }

    if (!this.endTimeString) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validierung',
        detail: 'Bitte geben Sie eine Endzeit an.'
      });
      return;
    }

    if (this.selectedDays.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validierung',
        detail: 'Bitte wählen Sie mindestens einen Wochentag aus.'
      });
      return;
    }

    if (this.endTimeString <= this.startTimeString) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validierung',
        detail: 'Die Endzeit muss nach der Startzeit liegen.'
      });
      return;
    }

    if (this.isEditMode()) {
      this.updateWorkingTime();
    } else {
      this.createWorkingTime();
    }
  }

  createWorkingTime(): void {
    const request: WorkingTimeCreateRequest = {
      accountId: this.selectedAccountId as any,
      days: this.selectedDays,
      startTime: this.startTimeString,
      endTime: this.endTimeString
    };

    this.workingTimesService.createWorkingTime(request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Arbeitszeit erstellt.'
        });
        this.showDialog.set(false);
        this.loadWorkingTimes();
        this.accountStateService.refresh();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: error.error?.detail || error.error?.message || 'Arbeitszeit konnte nicht erstellt werden.'
        });
      }
    });
  }

  updateWorkingTime(): void {
    if (!this.editingId) return;

    const request: WorkingTimeUpdateRequest = {
      days: this.selectedDays,
      startTime: this.startTimeString,
      endTime: this.endTimeString
    };

    this.workingTimesService.updateWorkingTime(this.editingId, request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Erfolg',
          detail: 'Arbeitszeit aktualisiert.'
        });
        this.showDialog.set(false);
        this.loadWorkingTimes();
        this.accountStateService.refresh();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Fehler',
          detail: error.error?.detail || error.error?.message || 'Arbeitszeit konnte nicht aktualisiert werden.'
        });
      }
    });
  }

  deleteWorkingTime(item: WorkingTimeResponse): void {
    this.confirmationService.confirm({
      message: 'Möchten Sie diese Arbeitszeit wirklich löschen?',
      header: 'Bestätigung',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        if (!item.id) return;

        this.workingTimesService.deleteWorkingTime(item.id.toString()).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Erfolg',
              detail: 'Arbeitszeit gelöscht.'
            });
            this.loadWorkingTimes();
            this.accountStateService.refresh();
          },
          error: (error) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Fehler',
              detail: 'Arbeitszeit konnte nicht gelöscht werden.'
            });
          }
        });
      }
    });
  }

  getAccountName(accountId: any): string {
    const account = this.accounts().find(a => a.accountId?.toString() === accountId?.toString());
    return account ? account.organizationName || 'Unbekannt' : 'Unbekannt';
  }

  formatDays(days: DayOfWeek[] | undefined): string {
    if (!days || days.length === 0) return '';
    const labels: Record<string, string> = {
      MONDAY: 'Mo',
      TUESDAY: 'Di',
      WEDNESDAY: 'Mi',
      THURSDAY: 'Do',
      FRIDAY: 'Fr',
      SATURDAY: 'Sa',
      SUNDAY: 'So'
    };
    const order = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
    return days
      .slice()
      .sort((a, b) => order.indexOf(a.toUpperCase()) - order.indexOf(b.toUpperCase()))
      .map(d => labels[d.toUpperCase()] ?? d)
      .join(', ');
  }

  calculateDuration(startTime: string | undefined, endTime: string | undefined): string {
    if (!startTime || !endTime) return '0';
    const [sh, sm] = startTime.split(':').map(Number);
    const [eh, em] = endTime.split(':').map(Number);
    const totalMinutes = (eh * 60 + em) - (sh * 60 + sm);
    if (totalMinutes <= 0) return '0';
    const hours = Math.floor(totalMinutes / 60);
    const mins = totalMinutes % 60;
    return mins === 0 ? `${hours}h` : `${hours}h ${mins}min`;
  }
}
