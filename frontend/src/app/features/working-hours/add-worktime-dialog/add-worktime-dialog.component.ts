import {
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Button } from 'primeng/button';
import { DayOfWeek, WorkingTimeCreateRequest, WorkingTimeUpdateRequest, AccountResponse } from '../../../api';
import { AuthService } from '../../../core/auth/auth.service';
import { OrgContextService } from '../../../core/services/org-context.service';

type OrgOption = {
  id: string;
  slug: string;
  role: string;
};

/**
 * Dialog component for creating and editing working times with optional org selection.
 *
 * - Shows org dropdown only if user is member of 2+ orgs (D-08)
 * - Selected org persists within dialog and clears on close (D-09)
 */
@Component({
  selector: 'app-add-worktime-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, SelectButtonModule, Button],
  templateUrl: './add-worktime-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddWorktimeDialogComponent implements OnInit, OnDestroy {
  @Input() isOpen = false;
  @Input() isEditMode = false;
  @Input() accounts: AccountResponse[] = [];

  @Output() save = new EventEmitter<WorkingTimeCreateRequest | WorkingTimeUpdateRequest>();
  @Output() closed = new EventEmitter<void>();

  private readonly authService = inject(AuthService);
  private readonly orgContextService = inject(OrgContextService);

  readonly userOrgs = signal<OrgOption[]>([]);
  readonly showOrgDropdown = signal(false);

  readonly dayOptions = [
    { label: 'Mo', value: DayOfWeek.Monday },
    { label: 'Di', value: DayOfWeek.Tuesday },
    { label: 'Mi', value: DayOfWeek.Wednesday },
    { label: 'Do', value: DayOfWeek.Thursday },
    { label: 'Fr', value: DayOfWeek.Friday },
    { label: 'Sa', value: DayOfWeek.Saturday },
    { label: 'So', value: DayOfWeek.Sunday },
  ];

  selectedDays: DayOfWeek[] = [];
  selectedAccountId: string | null = null;
  startTimeString: string = '08:00';
  endTimeString: string = '17:00';

  ngOnInit(): void {
    // Clear previous dialog org context (D-09)
    this.orgContextService.clearDialogOrg();

    // Load user orgs
    this.userOrgs.set(this.authService.getOrganizations());

    // Determine if dropdown should show
    this.showOrgDropdown.set(this.orgContextService.hasMultipleOrgs());

    // Initialize selected account
    if (this.accounts && this.accounts.length > 0) {
      this.selectedAccountId = this.accounts[0].accountId?.toString() || null;
    }
  }

  ngOnDestroy(): void {
    // Clear dialog context on close
    this.orgContextService.clearDialogOrg();
  }

  onOrgSelected(orgId: string): void {
    this.orgContextService.setDialogOrg(orgId);
  }

  onClose(): void {
    this.orgContextService.clearDialogOrg();
    this.closed.emit();
  }

  resetForm(): void {
    this.selectedDays = [];
    this.startTimeString = '08:00';
    this.endTimeString = '17:00';
    if (this.accounts && this.accounts.length > 0) {
      this.selectedAccountId = this.accounts[0].accountId?.toString() || null;
    }
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
      SUNDAY: 'So',
    };
    const order = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
    return (days as string[])
      .slice()
      .sort((a, b) => order.indexOf(a.toUpperCase()) - order.indexOf(b.toUpperCase()))
      .map((d) => labels[d.toUpperCase()] ?? d)
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

  onSave(): void {
    if (!this.selectedAccountId) {
      return;
    }

    if (!this.startTimeString) {
      return;
    }

    if (!this.endTimeString) {
      return;
    }

    if (this.selectedDays.length === 0) {
      return;
    }

    if (this.endTimeString <= this.startTimeString) {
      return;
    }

    if (this.isEditMode) {
      const payload: WorkingTimeUpdateRequest = {
        days: this.selectedDays,
        startTime: this.startTimeString,
        endTime: this.endTimeString,
      };
      this.save.emit(payload);
    } else {
      const payload: WorkingTimeCreateRequest = {
        accountId: this.selectedAccountId as any,
        days: this.selectedDays,
        startTime: this.startTimeString,
        endTime: this.endTimeString,
      };
      this.save.emit(payload);
    }
  }
}
