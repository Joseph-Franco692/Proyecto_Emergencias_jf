import { Component, OnInit, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { WebsocketService } from '../../services/websocket';
import { Subscription } from 'rxjs';
import { retry, timeout } from 'rxjs/operators';

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
  public imagenAbierta: boolean = false;
  public imagenAbiertaUrl: string = '';
  public classif: any = {
    severity: 'medium',
    title: 'REPORTE CIUDADANO',
    badge: 'MEDIO',
    icon: '',
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
  public reporteFinalizado: boolean = false;

  // IoT Telemetry & Post-fire habitability evaluation
  public bitacoraIot: any[] = [];
  public ultimaLecturaIot: any = null;
  public iotConectado: boolean = false;

  private API_URL = '/api/reportes';
  private UNIDADES_URL = '/api/unidades';
  private IOT_URL = '/api/iot';
  private wsSub!: Subscription;
  private iotSub!: Subscription;
  private iotSyncInterval: any;
  private reporteSyncInterval: any;

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
      this.http.get<any>(`${this.API_URL}/${this.idIncidente}`).pipe(
        timeout(10000),
        retry({ count: 2, delay: 500 })
      ).subscribe({
        next: (datos) => {
          this.classif = this.clasificarReporte(datos.descripcion);
          this.reporteSeleccionado = datos;
          this.aplicarEstadoReporte(datos);
          this.errorMessage = null;
          this.cdr.detectChanges();
          this.cargarEvidencias();
          this.cargarBitacoraIot();
        },
        error: (err) => {
          console.error('Error al cargar el reporte', err);
          this.errorMessage = err.status === 403
            ? 'Tu sesión no tiene autorización para consultar este incidente. Vuelve a iniciar sesión.'
            : err.status === 404
              ? 'El incidente solicitado ya no está disponible.'
              : 'No fue posible cargar el incidente después de varios intentos. Verifica la conexión con el servidor.';
          this.cdr.detectChanges();
        }
      });
    }

    this.reporteSyncInterval = setInterval(() => this.sincronizarEstadoReporte(), 3000);

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
              this.reporteFinalizado = true;
              this.isDispatched = false;
              if (this.reporteSeleccionado) {
                this.reporteSeleccionado = { ...this.reporteSeleccionado, estado: 'ATENDIDO' };
              }
            }
          } else if (evento?.tipo === 'ACTUALIZACION_INVENTARIO' && this.mostrarModalDespacho) {
            this.actualizarUnidadesDisponibles();
          }
          this.cdr.detectChanges();
        });
      }
    });

    // Escuchar telemetría IoT en tiempo real desde el nodo ESP32
    this.iotSub = this.wsService.escucharTelemetriaIot().subscribe({
      next: (telemetria) => {
        if (!telemetria) return;
        this.ngZone.run(() => {
          if (!this.idIncidente || String(telemetria.reporteId) === String(this.idIncidente) || telemetria.reporteId === 1) {
            console.log('📡 [IOT TELEMETRIA EN TIEMPO REAL RECIBIDA]:', telemetria);
            this.ultimaLecturaIot = telemetria;
            this.bitacoraIot = [telemetria, ...this.bitacoraIot];
            this.cdr.detectChanges();
          }
        });
      }
    });

    this.sincronizarEstadoIot();
    this.iotSyncInterval = setInterval(() => this.sincronizarEstadoIot(), 20000);
  }

  ngOnDestroy(): void {
    if (this.wsSub) this.wsSub.unsubscribe();
    if (this.iotSub) this.iotSub.unsubscribe();
    if (this.iotSyncInterval) clearInterval(this.iotSyncInterval);
    if (this.reporteSyncInterval) clearInterval(this.reporteSyncInterval);
  }

  private sincronizarEstadoReporte(): void {
    if (!this.idIncidente) return;
    this.http.get<any>(`${this.API_URL}/${this.idIncidente}`).subscribe({
      next: reporte => {
        this.reporteSeleccionado = { ...(this.reporteSeleccionado || {}), ...reporte };
        this.aplicarEstadoReporte(reporte);
        this.cdr.detectChanges();
      },
      error: err => console.error('Error sincronizando estado del reporte:', err)
    });
  }

  private aplicarEstadoReporte(reporte: any): void {
    this.reporteFinalizado = reporte?.estado === 'ATENDIDO';
    this.isDispatched = reporte?.estado === 'EN_ATENCION';
    if (this.reporteFinalizado) {
      this.despachoMensaje = 'Incidente finalizado y archivado como atendido.';
      localStorage.removeItem('dispatched_report_' + this.idIncidente);
    }
  }

  private cargarBitacoraIot(): void {
    if (!this.idIncidente) return;
    this.http.get<any[]>(`${this.IOT_URL}/reportes/${this.idIncidente}/bitacora`).subscribe({
      next: (bitacora) => {
        this.bitacoraIot = bitacora;
        if (bitacora && bitacora.length > 0) {
          this.ultimaLecturaIot = bitacora[0];
        }
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error cargando bitácora IoT:', err)
    });
  }

  private sincronizarEstadoIot(): void {
    if (!this.idIncidente) return;
    this.http.get<any[]>(`${this.IOT_URL}/reportes/${this.idIncidente}/sesiones`).subscribe({
      next: (sesiones) => {
        const activa = (sesiones || []).find(s => s.estado === 'ACTIVA');
        if (!activa) {
          this.iotConectado = false;
          this.cdr.detectChanges();
          return;
        }

        this.http.get<any[]>(`${this.IOT_URL}/reportes/${this.idIncidente}/bitacora`).subscribe({
          next: (bitacora) => {
            this.bitacoraIot = bitacora || [];
            // El estado visible no puede depender de una lectura antigua de otra
            // sesión: solo cuenta la telemetría de la sesión actualmente activa.
            const lecturasSesionActiva = this.bitacoraIot.filter(
              lectura => String(lectura.sesionId) === String(activa.id)
            );
            this.ultimaLecturaIot = lecturasSesionActiva[0] || null;
            const ultima = this.ultimaLecturaIot;
            const ultimaFecha = ultima?.fechaHora ? new Date(ultima.fechaHora).getTime() : 0;
            // El ESP32 publica cada pocos segundos. Si no hay una lectura reciente,
            // el nodo se muestra desconectado aunque una sesión antigua continúe abierta.
            const reciente = ultimaFecha > 0 && (Date.now() - ultimaFecha) < 15000;
            this.iotConectado = Boolean(ultima && reciente && ultima.evento !== 'FIN_SESION');
            this.cdr.detectChanges();
          },
          error: () => {
            this.iotConectado = false;
            this.cdr.detectChanges();
          }
        });
      },
      error: () => {
        this.iotConectado = false;
        this.cdr.detectChanges();
      }
    });
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
    if (this.reporteFinalizado) {
      this.despachoMensaje = 'Este incidente ya fue atendido y no admite nuevos despachos.';
      this.cdr.detectChanges();
      return;
    }
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
          this.despachoMensaje = 'Error al cargar unidades del servidor.';
          this.cdr.detectChanges();
        });
      }
    });
  }

  public cerrarModalDespacho(): void {
    this.mostrarModalDespacho = false;
    this.cdr.detectChanges();
  }

  private actualizarUnidadesDisponibles(): void {
    this.http.get<any[]>(`${this.UNIDADES_URL}/disponibles`).subscribe({
      next: unidades => {
        this.unidadesDisponibles = unidades;
        const idsVigentes = new Set(unidades.map(u => u.id));
        this.unidadesSeleccionadas.forEach(id => {
          if (!idsVigentes.has(id)) this.unidadesSeleccionadas.delete(id);
        });
        this.cdr.detectChanges();
      }
    });
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
    if (this.reporteFinalizado) {
      this.despachoMensaje = 'Este incidente ya fue atendido y no admite nuevos despachos.';
      this.cdr.detectChanges();
      return;
    }
    if (this.unidadesSeleccionadas.size === 0) {
      this.despachoMensaje = 'Selecciona al menos una unidad para despachar.';
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
          this.despachoMensaje = 'Error al despachar: ' + (err.error?.error || err.message);
          this.cdr.detectChanges();
        });
      }
    });
  }

  // ─── HELPERS DE UI ──────────────────────────────────────────────────────────

  public getMediaUrl(url: string): string {
    if (!url) return '';
    let cleanedUrl = url.replace(/\\/g, '/');
    if (cleanedUrl.startsWith('http://') || cleanedUrl.startsWith('https://')) {
      return cleanedUrl;
    }
    if (cleanedUrl.startsWith('/')) {
      cleanedUrl = cleanedUrl.substring(1);
    }
    return `/${cleanedUrl}`;
  }

  public abrirImagen(urlArchivo: string): void {
    this.imagenAbiertaUrl = this.getMediaUrl(urlArchivo);
    this.imagenAbierta = true;
    this.cdr.detectChanges();
  }

  public cerrarImagen(): void {
    this.imagenAbierta = false;
    this.imagenAbiertaUrl = '';
    this.cdr.detectChanges();
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

  private clasificarReporte(desc: string): any {
    const text = (desc || '').toLowerCase();
    if (text.includes('incendio') || text.includes('fuego') || text.includes('quema') || text.includes('atrapad') || text.includes('llama')) {
      if (text.includes('crítico') || text.includes('3 pisos') || text.includes('edificio') || text.includes('casa') || text.includes('industrial') || text.includes('residencial')) {
        return { severity: 'critical', title: 'INCENDIO ESTRUCTURAL', badge: 'CRÍTICO', icon: '', color: '#ff6b6b', aiTags: ['Humo denso', 'Peligro estructural', 'Llamas activas'], conf: '94.2%', mainTag: 'Peligro estructural' };
      }
      return { severity: 'critical', title: 'INCENDIO FORESTAL', badge: 'CRÍTICO', icon: '', color: '#ff6b6b', aiTags: ['Llamas activas', 'Propagación alta'], conf: '89.0%', mainTag: 'Propagación alta' };
    } else if (text.includes('gas') || text.includes('fuga') || text.includes('olor') || text.includes('derrame') || text.includes('quimic') || text.includes('colapso') || text.includes('derrumbe')) {
      if (text.includes('gas')) {
        return { severity: 'high', title: 'FUGA DE GAS', badge: 'ALTO', icon: '', color: '#f59e0b', aiTags: ['Gas inflamable', 'Zona de exclusión'], conf: '78.5%', mainTag: 'Zona de exclusión' };
      }
      if (text.includes('colapso') || text.includes('derrumbe') || text.includes('escombros')) {
        return { severity: 'high', title: 'COLAPSO PARCIAL', badge: 'ALTO', icon: '', color: '#f59e0b', aiTags: ['Estructura comprometida', 'Herido atrapado'], conf: '85.3%', mainTag: 'Riesgo colapso' };
      }
      return { severity: 'high', title: 'MAT. PELIGROSO', badge: 'ALTO', icon: '', color: '#f59e0b', aiTags: ['Sustancia corrosiva', 'Viento dispersor'], conf: '71.1%', mainTag: 'Sustancia nociva' };
    } else if (text.includes('choque') || text.includes('accidente') || text.includes('vial') || text.includes('colision') || text.includes('herido') || text.includes('inundacion') || text.includes('agua') || text.includes('desbordamiento')) {
      if (text.includes('inundacion') || text.includes('agua') || text.includes('desbordamiento') || text.includes('canal') || text.includes('barrio')) {
        return { severity: 'medium', title: 'INUNDACIÓN', badge: 'MEDIO', icon: '', color: '#60a5fa', aiTags: ['Acumulación agua', 'Zona baja'], conf: '82.4%', mainTag: 'Nivel agua alto' };
      }
      return { severity: 'medium', title: 'ACCIDENTE VIAL', badge: 'MEDIO', icon: '', color: '#60a5fa', aiTags: ['Colisión múltiple', 'Obstrucción vía'], conf: '77.8%', mainTag: 'Rescate necesario' };
    }
    return { severity: 'medium', title: 'REPORTE CIUDADANO', badge: 'MEDIO', icon: '', color: '#60a5fa', aiTags: ['Análisis pendiente'], conf: '60.0%', mainTag: 'Evaluación inicial' };
  }
}
