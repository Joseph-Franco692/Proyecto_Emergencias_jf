import { Component, OnInit, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { WebsocketService } from '../../services/websocket';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-detalle',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './detalle.html',
  styleUrls: ['./detalle.css']
})
export class DetalleComponent implements OnInit, OnDestroy {
  public reporteSeleccionado: any = null;
  public idIncidente: string | null = null;
  public evidencias: any[] = [];
  public isDispatched: boolean = false;
  public classif: any = {
    severity: 'medium',
    title: 'REPORTE CIUDADANO',
    badge: 'MEDIO',
    icon: '📍',
    color: '#60a5fa',
    aiTags: ['Análisis pendiente'],
    conf: '60.0%',
    mainTag: 'Evaluación inicial'
  };
  public errorMessage: string | null = null;

  // Despacho modal state
  public mostrarModalDespacho: boolean = false;
  public unidadesDisponibles: any[] = [];
  public unidadesSeleccionadas: Set<number> = new Set();
  public isLoadingUnidades: boolean = false;
  public isDespachandoUnidades: boolean = false;
  public despachoMensaje: string = '';
  public unidadesDespachadas: any[] = [];

  private API_URL = 'http://localhost:8081/api/reportes';
  private UNIDADES_URL = 'http://localhost:8081/api/unidades';
  private wsSub!: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    private wsService: WebsocketService
  ) {}

  ngOnInit(): void {
    this.idIncidente = this.route.snapshot.paramMap.get('id');

    if (this.idIncidente) {
      this.isDispatched = localStorage.getItem('dispatched_report_' + this.idIncidente) === 'true';
      this.http.get<any>(`${this.API_URL}/${this.idIncidente}`).subscribe({
        next: (datos) => {
          this.classif = this.clasificarReporte(datos.descripcion);
          this.reporteSeleccionado = datos;
          this.errorMessage = null;
          this.cdr.detectChanges();
          this.cargarEvidencias();
        },
        error: (err) => {
          console.error('Error al cargar el reporte', err);
          this.errorMessage = 'No se pudo obtener la información de la base de datos. Asegúrate de que el backend Spring Boot esté corriendo en el puerto 8081.';
          this.cdr.detectChanges();
        }
      });
    }

    // Escuchar eventos de unidades via WebSocket para actualizar UI en tiempo real
    this.wsSub = this.wsService.escucharUnidadesEstado().subscribe({
      next: (evento) => {
        this.ngZone.run(() => {
          if (evento?.tipo === 'DESPACHO' && String(evento.reporteId) === this.idIncidente) {
            this.isDispatched = true;
            this.unidadesDespachadas = evento.unidades || [];
            this.despachoMensaje = `✓ ${this.unidadesDespachadas.length} unidad(es) en ruta a esta emergencia`;
            localStorage.setItem('dispatched_report_' + this.idIncidente, 'true');
          } else if (evento?.tipo === 'LIBERACION' && String(evento.reporteAnteriorId) === this.idIncidente) {
            if (evento.reporteCerrado) {
              this.despachoMensaje = '✓ Todas las unidades se retiraron. Incidente cerrado.';
            }
          }
          this.cdr.detectChanges();
        });
      }
    });
  }

  ngOnDestroy(): void {
    if (this.wsSub) this.wsSub.unsubscribe();
  }

  private cargarEvidencias(): void {
    this.http.get<any[]>(`${this.API_URL}/${this.idIncidente}/evidencias`).subscribe({
      next: (evs) => {
        this.evidencias = evs;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error al cargar evidencias del reporte', err)
    });
  }

  // ─── DESPACHO MODAL ─────────────────────────────────────────────────────────

  public abrirModalDespacho(): void {
    this.mostrarModalDespacho = true;
    this.unidadesSeleccionadas.clear();
    this.despachoMensaje = '';
    this.isLoadingUnidades = true;
    this.cdr.detectChanges();

    this.http.get<any[]>(`${this.UNIDADES_URL}/disponibles`).subscribe({
      next: (unidades) => {
        this.ngZone.run(() => {
          this.unidadesDisponibles = unidades;
          this.isLoadingUnidades = false;
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        console.error('Error cargando unidades disponibles', err);
        this.ngZone.run(() => {
          this.isLoadingUnidades = false;
          this.despachoMensaje = '❌ Error al cargar unidades del servidor.';
          this.cdr.detectChanges();
        });
      }
    });
  }

  public cerrarModalDespacho(): void {
    this.mostrarModalDespacho = false;
    this.cdr.detectChanges();
  }

  public toggleUnidad(id: number): void {
    if (this.unidadesSeleccionadas.has(id)) {
      this.unidadesSeleccionadas.delete(id);
    } else {
      this.unidadesSeleccionadas.add(id);
    }
    this.cdr.detectChanges();
  }

  public isUnidadSeleccionada(id: number): boolean {
    return this.unidadesSeleccionadas.has(id);
  }

  public confirmarDespacho(): void {
    if (this.unidadesSeleccionadas.size === 0) {
      this.despachoMensaje = '⚠️ Selecciona al menos una unidad para despachar.';
      this.cdr.detectChanges();
      return;
    }

    this.isDespachandoUnidades = true;
    this.despachoMensaje = '';
    this.cdr.detectChanges();

    const ids = Array.from(this.unidadesSeleccionadas);

    this.http.put<any>(`${this.API_URL}/${this.idIncidente}/despachar`, ids).subscribe({
      next: (resp) => {
        this.ngZone.run(() => {
          this.isDespachandoUnidades = false;
          this.isDispatched = true;
          this.unidadesDespachadas = resp.unidadesDespachadas || [];
          this.despachoMensaje = `✓ ${this.unidadesDespachadas.length} unidad(es) despachadas con éxito.`;
          localStorage.setItem('dispatched_report_' + this.idIncidente, 'true');
          setTimeout(() => { this.mostrarModalDespacho = false; this.cdr.detectChanges(); }, 1800);
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        console.error('Error al despachar unidades', err);
        this.ngZone.run(() => {
          this.isDespachandoUnidades = false;
          this.despachoMensaje = '❌ Error al despachar: ' + (err.error?.error || err.message);
          this.cdr.detectChanges();
        });
      }
    });
  }

  // ─── HELPERS DE UI ──────────────────────────────────────────────────────────

  public getMediaUrl(url: string): string {
    if (!url) return '';
    const cleanedUrl = url.replace(/\\/g, '/');
    return `http://localhost:8081/${encodeURI(cleanedUrl)}`;
  }

  public formatTime(dateStr: string): string {
    if (!dateStr) return '--:--:--';
    try {
      return new Date(dateStr).toLocaleTimeString('es-EC', { hour12: false });
    } catch (e) { return '--:--:--'; }
  }

  public formatDateTime(dateStr: string): string {
    if (!dateStr) return 'Fecha desconocida';
    try {
      return new Date(dateStr).toLocaleString('es-EC', { hour12: false });
    } catch (e) { return dateStr; }
  }

  public getSeverityLevel(): string {
    if (this.classif.severity === 'critical') return '3';
    if (this.classif.severity === 'high') return '2';
    return '1';
  }



  public llamarReportero(): void {
    if (this.reporteSeleccionado?.celularReportero) {
      window.location.href = 'tel:' + this.reporteSeleccionado.celularReportero;
    } else {
      alert('No hay un número de celular disponible para este reporte.');
    }
  }

  public navegarCoordenadas(): void {
    if (this.reporteSeleccionado) {
      const lat = this.reporteSeleccionado.latitud;
      const lng = this.reporteSeleccionado.longitud;
      window.open(`https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`, '_blank');
    }
  }

  public cerrarIncidente(): void {
    if (this.idIncidente) {
      if (confirm('¿Está seguro de que desea cerrar y dar por resuelto este incidente?')) {
        localStorage.setItem('closed_report_' + this.idIncidente, 'true');
        this.router.navigate(['/']);
      }
    }
  }

  private clasificarReporte(desc: string): any {
    const text = (desc || '').toLowerCase();
    if (text.includes('incendio') || text.includes('fuego') || text.includes('quema') || text.includes('atrapad') || text.includes('llama')) {
      if (text.includes('crítico') || text.includes('3 pisos') || text.includes('edificio') || text.includes('casa') || text.includes('industrial') || text.includes('residencial')) {
        return { severity: 'critical', title: 'INCENDIO ESTRUCTURAL', badge: 'CRÍTICO', icon: '🔥', color: '#ff6b6b', aiTags: ['Humo denso', 'Peligro estructural', 'Llamas activas'], conf: '94.2%', mainTag: 'Peligro estructural' };
      }
      return { severity: 'critical', title: 'INCENDIO FORESTAL', badge: 'CRÍTICO', icon: '🌲', color: '#ff6b6b', aiTags: ['Llamas activas', 'Propagación alta'], conf: '89.0%', mainTag: 'Propagación alta' };
    } else if (text.includes('gas') || text.includes('fuga') || text.includes('olor') || text.includes('derrame') || text.includes('quimic') || text.includes('colapso') || text.includes('derrumbe')) {
      if (text.includes('gas')) {
        return { severity: 'high', title: 'FUGA DE GAS', badge: 'ALTO', icon: '⚠️', color: '#f59e0b', aiTags: ['Gas inflamable', 'Zona de exclusión'], conf: '78.5%', mainTag: 'Zona de exclusión' };
      }
      if (text.includes('colapso') || text.includes('derrumbe') || text.includes('escombros')) {
        return { severity: 'high', title: 'COLAPSO PARCIAL', badge: 'ALTO', icon: '🏗', color: '#f59e0b', aiTags: ['Estructura comprometida', 'Herido atrapado'], conf: '85.3%', mainTag: 'Riesgo colapso' };
      }
      return { severity: 'high', title: 'MAT. PELIGROSO', badge: 'ALTO', icon: '☣️', color: '#f59e0b', aiTags: ['Sustancia corrosiva', 'Viento dispersor'], conf: '71.1%', mainTag: 'Sustancia nociva' };
    } else if (text.includes('choque') || text.includes('accidente') || text.includes('vial') || text.includes('colision') || text.includes('herido') || text.includes('inundacion') || text.includes('agua') || text.includes('desbordamiento')) {
      if (text.includes('inundacion') || text.includes('agua') || text.includes('desbordamiento') || text.includes('canal') || text.includes('barrio')) {
        return { severity: 'medium', title: 'INUNDACIÓN', badge: 'MEDIO', icon: '💧', color: '#60a5fa', aiTags: ['Acumulación agua', 'Zona baja'], conf: '82.4%', mainTag: 'Nivel agua alto' };
      }
      return { severity: 'medium', title: 'ACCIDENTE VIAL', badge: 'MEDIO', icon: '🚗', color: '#60a5fa', aiTags: ['Colisión múltiple', 'Obstrucción vía'], conf: '77.8%', mainTag: 'Rescate necesario' };
    }
    return { severity: 'medium', title: 'REPORTE CIUDADANO', badge: 'MEDIO', icon: '📍', color: '#60a5fa', aiTags: ['Análisis pendiente'], conf: '60.0%', mainTag: 'Evaluación inicial' };
  }
}