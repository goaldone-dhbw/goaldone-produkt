import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH } from '../../api';
import { SuperAdminsPageComponent } from './super-admins-page.component';
import { ConfirmationService, MessageService } from 'primeng/api';

describe('SuperAdminsPageComponent', () => {
  let fixture: ComponentFixture<SuperAdminsPageComponent>;
  let component: SuperAdminsPageComponent;
  let httpMock: HttpTestingController;

  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SuperAdminsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: API_BASE },
        ConfirmationService,
        MessageService
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SuperAdminsPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(): void {
    fixture.detectChanges();

    const adminsRequest = httpMock.expectOne(`${API_BASE}/admins/super-admins`);
    adminsRequest.flush([]);

    fixture.detectChanges();
  }

  it('should create', () => {
    flushInitialRequests();
    expect(component).toBeTruthy();
  });

  it('should display empty message when no super admins exist', () => {
    flushInitialRequests();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Es sind noch keine Super-Admins vorhanden.');
  });
});
