import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { StartPageComponent } from './start-page.component';

describe('Startpage', () => {
  let component: StartPageComponent;
  let fixture: ComponentFixture<StartPageComponent>;
  let authServiceMock: any;

  beforeEach(async () => {
    authServiceMock = {
      hasValidAccessToken: vi.fn().mockReturnValue(false),
      initLoginFlow: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [StartPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {},
        },
        {
          provide: AuthService,
          useValue: authServiceMock,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StartPageComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
