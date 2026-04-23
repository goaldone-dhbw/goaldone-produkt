import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { By } from '@angular/platform-browser';
import { jest } from '@jest/globals';
import { DeleteSuperAdminDialogComponent } from './delete-super-admin-dialog.component';
import { SuperAdminService } from '../../api';

const mockAdmin = {
  userId: 'user-42',
  email: 'admin@example.com',
  status: 'ACTIVE',
  createdAt: new Date().toISOString(),
};

const mockSuperAdminService = {
  deleteSuperAdmin: jest.fn(),
};

describe('DeleteSuperAdminDialogComponent', () => {
  let component: DeleteSuperAdminDialogComponent;
  let fixture: ComponentFixture<DeleteSuperAdminDialogComponent>;

  beforeEach(async () => {
    jest.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [DeleteSuperAdminDialogComponent],
      providers: [
        { provide: SuperAdminService, useValue: mockSuperAdminService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeleteSuperAdminDialogComponent);
    component = fixture.componentInstance;
    component.admin = mockAdmin as any;
    fixture.detectChanges();
  });

   describe('Initialisierung', () => {
    it('sollte die Komponente erstellen', () => {
      expect(component).toBeTruthy();
    });

    it('sollte loading und errorMessage initial auf Standardwerte setzen', () => {
      expect(component.loading()).toBe(false);
      expect(component.errorMessage()).toBeNull();
    });

    it('sollte die E-Mail des zu löschenden Admins rendern', () => {
      const text = fixture.nativeElement.textContent;
      expect(text).toContain(mockAdmin.email);
    });
  });

  describe('cancel()', () => {
    it('sollte cancelled emittieren', () => {
      jest.spyOn(component.cancelled, 'emit');
      component.cancel();
      expect(component.cancelled.emit).toHaveBeenCalled();
    });

    it('sollte bei laufendem Request cancelled nicht emittieren', () => {
      jest.spyOn(component.cancelled, 'emit');
      component.loading.set(true);
      component.cancel();
      expect(component.cancelled.emit).not.toHaveBeenCalled();
    });
  });

  describe('confirm() – Mehrfachauslösung verhindern', () => {
    it('sollte bei laufendem Request keinen weiteren API-Call auslösen', () => {
      component.loading.set(true);
      component.confirm();
      expect(mockSuperAdminService.deleteSuperAdmin).not.toHaveBeenCalled();
    });
  });

  describe('confirm() – Erfolgreiche API-Response', () => {
    beforeEach(() => {
      mockSuperAdminService.deleteSuperAdmin.mockReturnValue(of(null));
    });

    it('sollte deleteSuperAdmin mit der userId des Admins aufrufen', () => {
      component.confirm();
      expect(mockSuperAdminService.deleteSuperAdmin).toHaveBeenCalledWith(mockAdmin.userId);
    });

    it('sollte deleted mit der userId des Admins emittieren', () => {
      jest.spyOn(component.deleted, 'emit');
      component.confirm();
      expect(component.deleted.emit).toHaveBeenCalledWith(mockAdmin.userId);
    });

    it('sollte loading nach Abschluss des Requests auf false setzen', () => {
      component.confirm();
      expect(component.loading()).toBe(false);
    });

    it('sollte keine errorMessage setzen', () => {
      component.confirm();
      expect(component.errorMessage()).toBeNull();
    });
  });

   describe('confirm() – Loading-State während des Requests', () => {
    it('sollte loading während des laufenden Requests auf true setzen', fakeAsync(() => {
      mockSuperAdminService.deleteSuperAdmin.mockReturnValue(
        of(null).pipe(delay(100)),
      );

      component.confirm();
      expect(component.loading()).toBe(true);

      tick(100);
      expect(component.loading()).toBe(false);
    }));

    it('sollte den Confirm-Button während des Requests deaktivieren', fakeAsync(() => {
      mockSuperAdminService.deleteSuperAdmin.mockReturnValue(
        of(null).pipe(delay(100)),
      );

      component.confirm();
      fixture.detectChanges();

      // Cancel-Button sollte ausgeblendet sein (showCancelButton=false bei loading)
      const cancelBtn = fixture.debugElement.query(
        By.css('[label="Abbrechen"]'),
      );
      expect(cancelBtn).toBeNull();

      tick(100);
    }));
  });

  // -----------------------------------------------------------------------
  // confirm() – Fehlerbehandlung
  // -----------------------------------------------------------------------
  describe('confirm() – Fehlerbehandlung', () => {
    beforeEach(() => {
      mockSuperAdminService.deleteSuperAdmin.mockReturnValue(
        throwError(() => ({ status: 500 })),
      );
    });

    it('sollte eine Fehlermeldung setzen', () => {
      component.confirm();
      expect(component.errorMessage()).toContain('Fehler beim Löschen');
    });

    it('sollte loading nach Fehler auf false setzen', () => {
      component.confirm();
      expect(component.loading()).toBe(false);
    });

    it('sollte deleted bei Fehler nicht emittieren', () => {
      jest.spyOn(component.deleted, 'emit');
      component.confirm();
      expect(component.deleted.emit).not.toHaveBeenCalled();
    });

    it('sollte cancelled bei Fehler nicht emittieren', () => {
      jest.spyOn(component.cancelled, 'emit');
      component.confirm();
      expect(component.cancelled.emit).not.toHaveBeenCalled();
    });

    it('sollte die Fehlermeldung im Template rendern', fakeAsync(() => {
      component.confirm();
      tick();
      fixture.detectChanges();
      const errorEl = fixture.debugElement.query(By.css('p-message[severity="error"]'));
      expect(errorEl).toBeTruthy();
    }));

    it('sollte nach Fehler erneut auslösbar sein', () => {
      component.confirm();
      mockSuperAdminService.deleteSuperAdmin.mockReturnValue(of(null));
      jest.spyOn(component.deleted, 'emit');
      component.confirm();
      expect(component.deleted.emit).toHaveBeenCalledWith(mockAdmin.userId);
    });
  });
});
