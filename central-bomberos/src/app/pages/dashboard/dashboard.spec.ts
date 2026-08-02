import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NEVER } from 'rxjs';

import { DashboardComponent } from './dashboard';
import { WebsocketService } from '../../services/websocket';
import { AuthService } from '../../services/auth.service';

describe('Dashboard', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        {
          provide: WebsocketService,
          useValue: {
            escucharNuevosReportes: () => NEVER,
            escucharUnidadesEstado: () => NEVER
          }
        },
        {
          provide: AuthService,
          useValue: {
            getUser$: () => NEVER,
            getUser: () => null
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should present the persisted report lifecycle clearly', () => {
    expect(component.getEstadoReporteLabel({ estado: 'PENDIENTE' })).toBe('PENDIENTE');
    expect(component.getEstadoReporteLabel({ estado: 'EN_ATENCION' })).toBe('EN ATENCIÓN');
  });
});
