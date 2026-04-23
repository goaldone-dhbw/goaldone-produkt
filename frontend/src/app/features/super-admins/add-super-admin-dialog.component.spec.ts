import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { By } from '@angular/platform-browser';
import { jest } from '@jest/globals';
import { AddSuperAdminDialogComponent } from './add-super-admin-dialog.component';
import { SuperAdminService } from '../../api';

const mockAdmin = {
  userId: 'user-1',
  email: 'test@example.com',
  status: 'ACTIVE',
  createdAt: new Date().toISOString(),
};

const mockSuperAdminService = {
  createSuperAdmin: jest.fn(),
};

describe('AddSuperAdminDialogComponent', () => {
  let component: AddSuperAdminDialogComponent;
  let fixture: ComponentFixture<AddSuperAdminDialogComponent>;

  beforeEach(async () => {
    jest.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [AddSuperAdminDialogComponent],
      providers: [
        { provide: SuperAdminService, useValue: mockSuperAdminService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AddSuperAdminDialogComponent);
    component = fixture.componentInstance;
    component.visible = true;
    fixture.detectChanges();
  });

  describe('Initialisierung', () => {
    it('sollte die Komponente erstellen', () => {
      expect(component).toBeTruthy();
    });

    it('sollte das E-Mail-Feld leer initialisieren', () => {
      expect(component.form.value.email).toBeFalsy();
    });

    it('sollte loading, errorMessage und invitationResult initial auf Standardwerte setzen', () => {
      expect(component.loading()).toBe(false);
      expect(component.errorMessage()).toBeNull();
      expect(component.invitationResult()).toBeNull();
    });
  });

   describe('Formularvalidierung', () => {
    it('sollte das Formular bei leerem E-Mail-Feld als ungültig markieren', () => {
      component.form.setValue({ email: '' });
      expect(component.form.invalid).toBe(true);
    });

    it('sollte das Formular bei ungültigem E-Mail-Format als ungültig markieren', () => {
      component.form.setValue({ email: 'kein-email-format' });
      expect(component.form.invalid).toBe(true);
    });

    it('sollte das Formular bei gültiger E-Mail als valid markieren', () => {
      component.form.setValue({ email: 'valid@example.com' });
      expect(component.form.valid).toBe(true);
    });

    it('sollte required-Fehler am E-Mail-Feld anzeigen wenn leer und touched', () => {
      component.form.get('email')!.markAsTouched();
      fixture.detectChanges();
      const errorEl = fixture.debugElement.query(
        By.css('[text="E-Mail ist erforderlich."]'),
      );
      expect(errorEl).toBeTruthy();
    });

    it('sollte email-Fehler am E-Mail-Feld anzeigen wenn Format ungültig und touched', () => {
      component.form.setValue({ email: 'kein-format' });
      component.form.get('email')!.markAsTouched();
      fixture.detectChanges();
      const errorEl = fixture.debugElement.query(
        By.css('[text="Bitte eine gültige E-Mail-Adresse eingeben."]'),
      );
      expect(errorEl).toBeTruthy();
    });
  });

   describe('submit() – Mehrfachauslösung verhindern', () => {
    it('sollte bei ungültigem Formular keinen API-Call auslösen', () => {
      component.submit();
      expect(mockSuperAdminService.createSuperAdmin).not.toHaveBeenCalled();
    });

    it('sollte bei ungültigem Formular alle Felder als touched markieren', () => {
      component.submit();
      expect(component.form.get('email')?.touched).toBe(true);
    });

    it('sollte bei laufendem Request keinen weiteren API-Call auslösen', () => {
      component.form.setValue({ email: 'test@example.com' });
      component.loading.set(true);
      component.submit();
      expect(mockSuperAdminService.createSuperAdmin).not.toHaveBeenCalled();
    });
  });

   describe('submit() – Erfolgreiche API-Response', () => {
    beforeEach(() => {
      component.form.setValue({ email: 'test@example.com' });
      mockSuperAdminService.createSuperAdmin.mockReturnValue(of(mockAdmin));
    });

    it('sollte createSuperAdmin mit der eingegebenen E-Mail aufrufen', () => {
      component.submit();
      expect(mockSuperAdminService.createSuperAdmin).toHaveBeenCalledWith({
        email: 'test@example.com',
      });
    });

    it('sollte invitationResult nach erfolgreicher Response setzen', () => {
      component.submit();
      expect(component.invitationResult()).toEqual(mockAdmin);
    });

    it('sollte added-Event mit dem zurückgegebenen Admin emittieren', () => {
      jest.spyOn(component.added, 'emit');
      component.submit();
      expect(component.added.emit).toHaveBeenCalledWith(mockAdmin);
    });

    it('sollte loading nach Abschluss des Requests auf false setzen', () => {
      component.submit();
      expect(component.loading()).toBe(false);
    });

    it('sollte keine errorMessage setzen', () => {
      component.submit();
      expect(component.errorMessage()).toBeNull();
    });

    it('sollte die Bestätigungsmeldung mit der E-Mail rendern', fakeAsync(() => {
      component.submit();
      tick();
      fixture.detectChanges();
      const confirmText = fixture.nativeElement.textContent;
      expect(confirmText).toContain('test@example.com');
    }));
  });


   describe('submit() – Loading-State während des Requests', () => {
    it('sollte loading während des laufenden Requests auf true setzen', fakeAsync(() => {
      component.form.setValue({ email: 'test@example.com' });
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        of(mockAdmin).pipe(delay(100)),
      );

      component.submit();
      expect(component.loading()).toBe(true);

      tick(100);
      expect(component.loading()).toBe(false);
    }));
  });

  describe('submit() – Fehlerbehandlung', () => {
    beforeEach(() => {
      component.form.setValue({ email: 'test@example.com' });
    });

    it('sollte bei 409 (Einladung existiert) die passende Fehlermeldung anzeigen', () => {
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        throwError(() => ({
          status: 409,
          error: { detail: 'super-admin-invitation-already-exists' },
        })),
      );
      component.submit();
      expect(component.errorMessage()).toContain('bereits eine offene Einladung');
    });

    it('sollte bei 400 mit errors-Array die erste Fehlermeldung anzeigen', () => {
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        throwError(() => ({
          status: 400,
          error: { errors: [{ message: 'Ungültige E-Mail' }] },
        })),
      );
      component.submit();
      expect(component.errorMessage()).toBe('Ungültige E-Mail');
    });

    it('sollte bei unbekanntem Fehler eine generische Fehlermeldung anzeigen', () => {
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        throwError(() => ({ status: 500 })),
      );
      component.submit();
      expect(component.errorMessage()).toContain('Fehler beim Einladen');
    });

    it('sollte loading nach Fehler auf false setzen', () => {
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        throwError(() => ({ status: 500 })),
      );
      component.submit();
      expect(component.loading()).toBe(false);
    });

    it('sollte invitationResult bei Fehler nicht setzen', () => {
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        throwError(() => ({ status: 500 })),
      );
      component.submit();
      expect(component.invitationResult()).toBeNull();
    });

    it('sollte die Fehlermeldung im Template rendern', fakeAsync(() => {
      mockSuperAdminService.createSuperAdmin.mockReturnValue(
        throwError(() => ({ status: 500 })),
      );
      component.submit();
      tick();
      fixture.detectChanges();
      const errorEl = fixture.debugElement.query(By.css('p-message[severity="error"]'));
      expect(errorEl).toBeTruthy();
    }));
  });

  describe('close()', () => {
    it('sollte visibleChange mit false emittieren', () => {
      jest.spyOn(component.visibleChange, 'emit');
      component.close();
      expect(component.visibleChange.emit).toHaveBeenCalledWith(false);
    });

    it('sollte das Formular zurücksetzen', () => {
      component.form.setValue({ email: 'test@example.com' });
      component.close();
      expect(component.form.value.email).toBeFalsy();
    });

    it('sollte errorMessage und invitationResult zurücksetzen', () => {
      component.errorMessage.set('Ein Fehler');
      component.invitationResult.set(mockAdmin as any);
      component.close();
      expect(component.errorMessage()).toBeNull();
      expect(component.invitationResult()).toBeNull();
    });
  });
});
