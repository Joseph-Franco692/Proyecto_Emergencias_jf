import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NEVER } from 'rxjs';

import { DetalleComponent } from './detalle';
import { WebsocketService } from '../../services/websocket';

describe('Detalle', () => {
  let component: DetalleComponent;
  let fixture: ComponentFixture<DetalleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetalleComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        {
          provide: WebsocketService,
          useValue: {
            escucharNuevosReportes: () => NEVER,
            escucharUnidadesEstado: () => NEVER
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DetalleComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
