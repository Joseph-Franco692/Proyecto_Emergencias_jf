import { Component, OnInit, OnDestroy, NgZone, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common'; 
import { HttpClient } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router'; 
import { FormsModule } from '@angular/forms';
import { WebsocketService } from '../../services/websocket';
import { Subscription, timeout } from 'rxjs';
import { AuthService, AppUser } from '../../services/auth.service';
import * as L from 'leaflet';
import { ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule], 
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
  encapsulation: ViewEncapsulation.None
})
export class DashboardComponent implements OnInit, OnDestroy { 
  public listaReportes: any[] = [];
  private mapa!: L.Map;
  private markersMap: Map<number, L.Marker> = new Map();
  private newlyArrivedIds: Set<number> = new Set();
  private wsSub!: Subscription;
  
  // Real-time properties
  public clockTime: string = '--:--:--';
  public cpuUsage: number = 34;
  public ramUsage: number = 1.2;
  public wsMsgsMin: number = 23;
  public threadStats: any = {
    activeCount: 0,
    poolSize: 0,
    corePoolSize: 4,
    maxPoolSize: 8,
    queueSize: 0
  };

  private API_URL = '/api/reportes';
  private clockInterval: any;
  private threadInterval: any;
  private metricsInterval: any;
  private reportesSyncInterval: any;
  public adminNotice: string = '';
  public unidadesPorReporte: Record<number, any[]> = {};

  // CRUD Unidades
  public showUnidadesModal: boolean = false;
  public listaUnidades: any[] = [];
  public nuevaUnidad = { nombre: '', tipo: '' };
  private UNIDADES_API_URL = '/api/unidades';

  // Reportes Finales (Bitácoras)
  public showReportesModal: boolean = false;
  public listaReportesFinales: any[] = [];

  // Historial Completo de Incidentes
  public showHistorialModal: boolean = false;
  public listaHistorialCompleto: any[] = [];

  // Gestión de Usuarios (Admin)
  public showUsuariosModal: boolean = false;
  public listaUsuarios: any[] = [];

  // 🤖 Copiloto operativo con respuestas verificadas desde PostgreSQL
  public showAiChat: boolean = false;
  public preguntaAi: string = '';
  public isAiThinking: boolean = false;
  public aiMessages: any[] = [
    {
      sender: 'ai',
      text: 'Hola. Soy tu <strong>Copiloto Operativo IA</strong>. Puedo ayudarte con emergencias, reportes, unidades, operadores, despachos, bitácoras y monitoreo IoT de este sistema.',
      time: new Date().toLocaleTimeString('es-EC', { hour: '2-digit', minute: '2-digit' })
    }
  ];
  private AI_URL = '/api/ai/chat';

  // Usuario actual
  public currentUser$: any;
  public currentUser: AppUser | null = null;

  constructor(
    private http: HttpClient, 
    private wsService: WebsocketService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private router: Router
  ) {
    this.currentUser$ = this.authService.getUser$();
    this.authService.getUser$().subscribe(user => {
      this.currentUser = user;
      this.cdr.detectChanges();
    });
  }

  ngOnInit(): void {
    this.iniciarMapa();
    this.cargarHistorial(false);
    this.iniciarReloj();
    this.iniciarMonitoreoHilos();
    this.iniciarMetricasSimuladas();

    // ─── SUSCRIPCIÓN WEBSOCKET: REPORTES EN TIEMPO REAL ─────────────────────────
    this.wsSub = this.wsService.escucharNuevosReportes().subscribe({
      next: (reporteRecibido: any) => {
        // Parseo defensivo del payload
        const rep = typeof reporteRecibido === 'string'
          ? JSON.parse(reporteRecibido)
          : reporteRecibido;

        // REGLA 4: Manejo de tipos en Leaflet (Casteo estricto a Number)
        const id = Number(rep.id);
        const lat = Number(rep.latitud);
        const lng = Number(rep.longitud);

        console.log(`[WS-DASHBOARD] Reporte #${id} recibido en tiempo real (${lat}, ${lng})`);

        // REGLA 2: Contexto de Ejecución (NgZone)
        this.ngZone.run(() => {
          
          this.newlyArrivedIds.add(id);
          setTimeout(() => {
            this.newlyArrivedIds.delete(id);
            this.cdr.detectChanges();
          }, 5000);

          // REGLA 1: Inmutabilidad Absoluta (Reconstrucción del array)
          const yaExiste = this.listaReportes.some(r => Number(r.id) === id);
          if (!yaExiste) {
            this.listaReportes = [rep, ...this.listaReportes];
          }

          this.agregarMarcador(rep);

          // REGLA 4 (Aplicada): Coordenadas numéricas para la animación
          if (this.mapa && !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0) {
            this.mapa.flyTo([lat, lng], 16, { animate: true, duration: 1.2 });
            setTimeout(() => {
              const marker = this.markersMap.get(id);
              if (marker) marker.openPopup();
            }, 400);
          }

          // REGLA 3: Detección de Cambios Manual
          this.cdr.detectChanges();
        });
      },
      error: (err: any) => console.error('[WS-DASHBOARD] Error en suscripción WebSocket:', err)
    });

    this.wsService.escucharUnidadesEstado().subscribe({
      next: (evento) => {
        if (evento?.tipo === 'DESPACHO') {
          this.ngZone.run(() => {
            this.actualizarEstadoLocal(Number(evento.reporteId), 'EN_ATENCION');
            this.cargarEstadoOperativo();
          });
        }
        if (evento?.tipo === 'LLEGADA_SITIO') {
          this.ngZone.run(() => this.cargarEstadoOperativo());
        }
        if (evento?.tipo === 'LIBERACION') {
          this.ngZone.run(() => {
            if (evento.reporteCerrado) {
              this.notificarCierre(Number(evento.reporteAnteriorId));
            }
            this.cargarHistorial(false);
            this.cargarEstadoOperativo();
          });
        }
        if (evento?.tipo === 'ACTUALIZACION_INVENTARIO' && this.showUnidadesModal) {
          this.ngZone.run(() => {
            this.cargarUnidades();
          });
        }
        if (evento?.tipo === 'NUEVO_REPORTE_FINAL' && this.showReportesModal) {
          this.ngZone.run(() => {
            this.cargarReportesFinales();
          });
        }
      }
    });

    // Respaldo distribuido: con dos réplicas el broker STOMP es local a cada
    // proceso. PostgreSQL se consulta periódicamente para que el tablero
    // converja aunque el evento haya sido publicado por la otra réplica.
    this.reportesSyncInterval = setInterval(() => {
      this.cargarHistorial(true);
      this.cargarEstadoOperativo();
    }, 3000);
    this.cargarEstadoOperativo();
  }







  ngOnDestroy(): void {
    if (this.wsSub) this.wsSub.unsubscribe();
    if (this.clockInterval) clearInterval(this.clockInterval);
    if (this.threadInterval) clearInterval(this.threadInterval);
    if (this.metricsInterval) clearInterval(this.metricsInterval);
    if (this.reportesSyncInterval) clearInterval(this.reportesSyncInterval);
  }

  private iniciarReloj(): void {
    const updateTime = () => {
      const now = new Date();
      this.clockTime = now.toLocaleTimeString('es-EC', { hour12: false });
      this.cdr.detectChanges();
    };
    updateTime();
    this.clockInterval = setInterval(updateTime, 1000);
  }

  private iniciarMonitoreoHilos(): void {
    const fetchHilos = () => {
      this.http.get<any>(`${this.API_URL}/stats/hilos`).subscribe({
        next: (stats) => {
          this.ngZone.run(() => {
            this.threadStats = stats;
            this.cdr.detectChanges();
          });
        },
        error: (err) => console.error('Error cargando estadísticas de hilos:', err)
      });
    };
    fetchHilos();
    this.threadInterval = setInterval(fetchHilos, 5000);
  }

  private iniciarMetricasSimuladas(): void {
    this.metricsInterval = setInterval(() => {
      this.ngZone.run(() => {
        this.cpuUsage = Math.floor(25 + Math.random() * 20);
        this.ramUsage = Number((1.1 + Math.random() * 0.2).toFixed(1));
        this.wsMsgsMin = Math.floor(18 + Math.random() * 12);
        this.cdr.detectChanges();
      });
    }, 3000);
  }

  private iniciarMapa(): void {
    this.mapa = L.map('mapa-bomberos', {
      zoomControl: false
    }).setView([-0.253012, -79.177024], 13);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains: 'abcd',
      maxZoom: 19,
      tileSize: 256,
      detectRetina: true
    }).addTo(this.mapa);

    L.control.zoom({ position: 'topright' }).addTo(this.mapa);

    setTimeout(() => this.mapa.invalidateSize(), 500);
  }

  private cargarHistorial(notificarCambios: boolean = false): void {
    this.http.get<any[]>(this.API_URL).subscribe({
      next: (historial) => {
        this.ngZone.run(() => {
          const activosAnteriores = new Set(this.listaReportes.map(rep => Number(rep.id)));
          const ordenados = [...historial].sort((a, b) =>
            new Date(b.fechaReporte || 0).getTime() - new Date(a.fechaReporte || 0).getTime());
          const activos = ordenados.filter(rep => rep.estado !== 'ATENDIDO');

          if (notificarCambios) {
            ordenados
              .filter(rep => rep.estado === 'ATENDIDO' && activosAnteriores.has(Number(rep.id)))
              .forEach(rep => this.notificarCierre(Number(rep.id)));
          }

          const idsActivos = new Set(activos.map(rep => Number(rep.id)));
          this.markersMap.forEach((marker, id) => {
            if (!idsActivos.has(id)) {
              this.mapa.removeLayer(marker);
              this.markersMap.delete(id);
            }
          });

          this.listaReportes = activos;
          activos.forEach(rep => this.agregarMarcador(rep));
          this.cdr.detectChanges();
        });
      },
      error: (err) => console.error('Error cargando historial de reportes:', err)
    });
  }

  private cargarEstadoOperativo(): void {
    this.http.get<any[]>(`${this.UNIDADES_API_URL}/estado-operativo`).subscribe({
      next: (unidades) => {
        const agrupadas: Record<number, any[]> = {};
        (unidades || []).forEach(unidad => {
          const reporteId = Number(unidad.reporteId);
          if (!Number.isFinite(reporteId) || reporteId <= 0) return;
          agrupadas[reporteId] = [...(agrupadas[reporteId] || []), unidad];
        });
        this.unidadesPorReporte = agrupadas;
        this.cdr.detectChanges();
      },
      error: err => console.error('Error sincronizando estado operativo:', err)
    });
  }

  private actualizarEstadoLocal(reporteId: number, estado: string): void {
    this.listaReportes = this.listaReportes.map(rep =>
      Number(rep.id) === reporteId ? { ...rep, estado } : rep);
    this.cdr.detectChanges();
  }

  private notificarCierre(reporteId: number): void {
    if (!reporteId) return;
    this.adminNotice = `Incidente #${reporteId} finalizado por la unidad operativa. El reporte quedó archivado como atendido.`;
    this.listaReportes = this.listaReportes.filter(rep => Number(rep.id) !== reporteId);
    const marker = this.markersMap.get(reporteId);
    if (marker) {
      this.mapa.removeLayer(marker);
      this.markersMap.delete(reporteId);
    }
    setTimeout(() => {
      if (this.adminNotice.includes(`#${reporteId}`)) {
        this.adminNotice = '';
        this.cdr.detectChanges();
      }
    }, 8000);
    this.cdr.detectChanges();
  }

  public getEstadoReporteLabel(reporte: any): string {
    return reporte?.estado === 'EN_ATENCION' ? 'EN ATENCIÓN' : 'PENDIENTE';
  }

  private agregarMarcador(rep: any): void {
    if (!rep || rep.latitud === undefined || rep.longitud === undefined) return;
    const lat = Number(rep.latitud);
    const lng = Number(rep.longitud);
    const repId = Number(rep.id);
    if (isNaN(lat) || isNaN(lng) || lat === 0 || lng === 0) return;

    if (this.markersMap.has(repId)) {
      return;
    }

    const classif = this.clasificarReporte(rep.descripcion);
    const shortTitle = classif.title.split(' ').slice(0, 2).join(' ');

    const pinIcon = L.divIcon({
      className: 'custom-leaflet-marker',
      html: `
        <div class="map-pin">
          <div class="google-gps-pin">
            <svg viewBox="0 0 24 24" width="40" height="48" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" fill="#ea4335" stroke="#ffffff" stroke-width="1.8"/>
            </svg>
          </div>
          <div class="pin-label"><span>#${repId}</span>${shortTitle}</div>
        </div>
      `,
      iconSize: [40, 50],
      iconAnchor: [20, 48],
      popupAnchor: [0, -48]
    });

    const marcador = L.marker([lat, lng], { icon: pinIcon }).addTo(this.mapa);
    marcador.bindPopup(`
      <div class="incident-map-popup">
        <div class="incident-map-popup__heading">
          <span class="incident-map-popup__type" style="--incident-color:${classif.color}">${classif.icon} ${classif.title}</span>
          <span class="incident-map-popup__id">#${repId}</span>
        </div>
        ${rep.iaLabel ? `<div class="incident-map-popup__ai">IA: ${rep.iaLabel} (${rep.iaConfidence}%)</div>` : ''}
        <p class="incident-map-popup__description">${rep.descripcion}</p>
        <a class="incident-map-popup__link" href="/detalle/${repId}">Ver detalles del incidente <span aria-hidden="true">→</span></a>
      </div>
    `, {
      closeButton: false,
      className: 'custom-popup'
    });

    this.markersMap.set(repId, marcador);
  }

  public selectReport(rep: any): void {
    if (!rep || rep.latitud === undefined || rep.longitud === undefined) return;
    const lat = Number(rep.latitud);
    const lng = Number(rep.longitud);
    const repId = Number(rep.id);
    if (isNaN(lat) || isNaN(lng) || lat === 0 || lng === 0) return;

    this.ngZone.run(() => {
      if (this.mapa) {
        this.mapa.flyTo([lat, lng], 16, { animate: true, duration: 1.2 });
      }
      setTimeout(() => {
        const marker = this.markersMap.get(repId);
        if (marker) {
          marker.openPopup();
        }
      }, 350);
      this.cdr.detectChanges();
    });
  }

  public limpiarTodosLosIncidentes(): void {
    // El panel no debe cerrar incidentes únicamente en el navegador. El cierre
    // válido lo realiza la última unidad operativa y queda persistido en PostgreSQL.
    this.cargarHistorial(false);
    this.cargarEstadoOperativo();
    this.adminNotice = 'Panel sincronizado con el estado operativo registrado.';
    setTimeout(() => {
      if (this.adminNotice === 'Panel sincronizado con el estado operativo registrado.') {
        this.adminNotice = '';
        this.cdr.detectChanges();
      }
    }, 4000);
    this.cdr.detectChanges();
  }

  public clasificarReporte(desc: string): any {
    const text = (desc || '').toLowerCase();
    if (text.includes('incendio') || text.includes('fuego') || text.includes('quema') || text.includes('atrapad') || text.includes('llama')) {
      if (text.includes('crítico') || text.includes('3 pisos') || text.includes('edificio') || text.includes('casa') || text.includes('industrial') || text.includes('residencial')) {
        return {
          severity: 'critical',
          title: 'INCENDIO ESTRUCTURAL',
          badge: 'CRÍTICO',
          icon: '',
          color: '#ff6b6b',
          aiTags: ['Humo denso', 'Peligro estructural', 'Llamas activas'],
          conf: '94%',
          mainTag: 'Peligro estructural'
        };
      }
      return {
        severity: 'critical',
        title: 'INCENDIO FORESTAL',
        badge: 'CRÍTICO',
        icon: '',
        color: '#ff6b6b',
        aiTags: ['Llamas activas', 'Propagación alta'],
        conf: '89%',
        mainTag: 'Propagación alta'
      };
    } else if (text.includes('gas') || text.includes('fuga') || text.includes('olor') || text.includes('derrame') || text.includes('quimic') || text.includes('colapso') || text.includes('derrumbe')) {
      if (text.includes('gas')) {
        return {
          severity: 'high',
          title: 'FUGA DE GAS',
          badge: 'ALTO',
          icon: '',
          color: '#f59e0b',
          aiTags: ['Gas inflamable', 'Zona de exclusión'],
          conf: '78%',
          mainTag: 'Zona de exclusión'
        };
      }
      if (text.includes('colapso') || text.includes('derrumbe') || text.includes('escombros')) {
        return {
          severity: 'high',
          title: 'COLAPSO PARCIAL',
          badge: 'ALTO',
        icon: '',
          color: '#f59e0b',
          aiTags: ['Estructura comprometida', 'Herido atrapado'],
          conf: '85%',
          mainTag: 'Riesgo colapso'
        };
      }
      return {
        severity: 'high',
        title: 'MAT. PELIGROSO',
        badge: 'ALTO',
        icon: '',
        color: '#f59e0b',
        aiTags: ['Sustancia corrosiva', 'Viento dispersor'],
        conf: '71%',
        mainTag: 'Sustancia nociva'
      };
    } else if (text.includes('choque') || text.includes('accidente') || text.includes('vial') || text.includes('colision') || text.includes('herido') || text.includes('inundacion') || text.includes('agua') || text.includes('desbordamiento')) {
      if (text.includes('inundacion') || text.includes('agua') || text.includes('desbordamiento') || text.includes('canal') || text.includes('barrio')) {
        return {
          severity: 'medium',
          title: 'INUNDACIÓN',
          badge: 'MEDIO',
          icon: '',
          color: '#60a5fa',
          aiTags: ['Acumulación agua', 'Zona baja'],
          conf: '82%',
          mainTag: 'Nivel agua alto'
        };
      }
      return {
        severity: 'medium',
        title: 'ACCIDENTE VIAL',
        badge: 'MEDIO',
        icon: '',
        color: '#60a5fa',
        aiTags: ['Colisión múltiple', 'Obstrucción vía'],
        conf: '77%',
        mainTag: 'Rescate necesario'
      };
    }
    return {
      severity: 'medium',
      title: 'REPORTE CIUDADANO',
      badge: 'MEDIO',
      icon: '',
      color: '#60a5fa',
      aiTags: ['Análisis pendiente'],
      conf: '60%',
      mainTag: 'Evaluación inicial'
    };
  }

  public getSeverityClass(rep: any): string {
    return this.clasificarReporte(rep.descripcion).severity;
  }
  public getSeverityIcon(rep: any): string {
    return this.clasificarReporte(rep.descripcion).icon;
  }
  public getSeverityTitle(rep: any): string {
    return this.clasificarReporte(rep.descripcion).title;
  }
  public getSeverityBadge(rep: any): string {
    return this.clasificarReporte(rep.descripcion).badge;
  }
  public getSeverityColor(rep: any): string {
    return this.clasificarReporte(rep.descripcion).color;
  }

  public isNewAlert(rep: any): boolean {
    return this.newlyArrivedIds.has(rep.id);
  }

  public formatTime(dateStr: string): string {
    try {
      const date = new Date(dateStr);
      return date.toLocaleTimeString('es-EC', { hour12: false });
    } catch (e) {
      return '--:--:--';
    }
  }

  public activeIncidentsCount(): number {
    return this.listaReportes.filter(r => {
      const c = this.clasificarReporte(r.descripcion);
      return c.severity === 'critical' || c.severity === 'high';
    }).length;
  }

  public getThreadDots(): string[] {
    if (!this.threadStats) return [];
    const dots: string[] = [];
    const active = this.threadStats.activeCount;
    const max = this.threadStats.maxPoolSize;
    for (let i = 0; i < max; i++) {
      if (i < active) {
        dots.push('active');
      } else if (i < this.threadStats.poolSize) {
        dots.push('busy');
      } else {
        dots.push('');
      }
    }
    return dots;
  }

  // ─── MÉTODOS CRUD DE UNIDADES ───
  public abrirModalUnidades(): void {
    this.showUnidadesModal = true;
    this.cargarUnidades();
  }

  public cerrarModalUnidades(): void {
    this.showUnidadesModal = false;
  }

  public cargarUnidades(): void {
    this.http.get<any[]>(this.UNIDADES_API_URL).subscribe({
      next: (data) => {
        this.listaUnidades = data;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error cargando unidades:', err)
    });
  }

  public agregarUnidad(): void {
    const nombre = this.nuevaUnidad.nombre.trim().replace(/\s+/g, ' ');
    const tipo = this.nuevaUnidad.tipo.trim().replace(/\s+/g, ' ');
    if (!/^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 -]{2,60}$/.test(nombre)
        || !/^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 /-]{2,60}$/.test(tipo)) {
      alert('Ingresa un nombre y tipo de unidad válidos.');
      return;
    }
    this.nuevaUnidad = { nombre, tipo };
    this.http.post<any>(this.UNIDADES_API_URL, this.nuevaUnidad).subscribe({
      next: () => {
        this.nuevaUnidad = { nombre: '', tipo: '' };
        this.cargarUnidades();
      },
      error: (err) => alert(
        err.error?.error || err.error?.message || 'No se pudo registrar la unidad.')
    });
  }

  public eliminarUnidad(id: number): void {
    if (confirm('¿Seguro que deseas eliminar esta unidad permanentemente?')) {
      this.http.delete(`${this.UNIDADES_API_URL}/${id}`).subscribe({
        next: () => this.cargarUnidades(),
        error: (err) => alert(
          err.error?.error || err.error?.message || 'No se pudo eliminar la unidad.')
      });
    }
  }

  public forzarEstado(id: number, event: any): void {
    const nuevoEstado = event.target.value;
    this.http.put(`${this.UNIDADES_API_URL}/${id}/estado?estado=${nuevoEstado}`, {}).subscribe({
      next: () => this.cargarUnidades(),
      error: (err) => {
        alert('No se cambió el estado: '
          + (err.error?.error || err.error?.message || err.message));
        this.cargarUnidades();
      }
    });
  }

  // ─── LÓGICA DE REPORTES FINALES ───
  public abrirModalReportes(): void {
    this.showReportesModal = true;
    this.cargarReportesFinales();
  }

  public cerrarModalReportes(): void {
    this.showReportesModal = false;
  }

  private cargarReportesFinales(): void {
    this.http.get<any[]>(`${this.UNIDADES_API_URL}/reportes-finales`).subscribe({
      next: (data) => {
        this.listaReportesFinales = data;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error cargando reportes finales', err)
    });
  }

  // ─── LÓGICA DE HISTORIAL DE INCIDENTES ───
  public abrirModalHistorial(): void {
    this.showHistorialModal = true;
    this.cargarHistorialCompleto();
  }

  public cerrarModalHistorial(): void {
    this.showHistorialModal = false;
  }

  private cargarHistorialCompleto(): void {
    this.http.get<any[]>(this.API_URL).subscribe({
      next: (data) => {
        this.listaHistorialCompleto = data.reverse();
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error cargando historial completo', err)
    });
  }

  // ─── GESTIÓN DE USUARIOS Y ROLES (ADMIN) ───
  public abrirModalUsuarios(): void {
    this.showUsuariosModal = true;
    this.cargarUsuarios();
  }

  public cerrarModalUsuarios(): void {
    this.showUsuariosModal = false;
  }

  public cargarUsuarios(): void {
    this.authService.getUsers().subscribe({
      next: (data) => {
        this.listaUsuarios = data;
        this.cdr.detectChanges();
      },
      error: (err) => alert('Error al cargar usuarios: ' + err.message)
    });
  }

  public cambiarRolUsuario(userId: string, nuevoRol: string): void {
    this.authService.updateUserRole(userId, nuevoRol).subscribe({
      next: (resp) => {
        alert(resp.message || 'Rol actualizado correctamente');
        this.cargarUsuarios();
      },
      error: (err) => alert('Error actualizando rol: ' + err.message)
    });
  }

  public copiarCodigoZona(code: string): void {
    if (!code) return;
    navigator.clipboard.writeText(code);
    alert('Código de zona copiado al portapapeles:\n' + code + '\n\nCompártelo con tus operadores para que se vinculen al centro de mando.');
  }

  public logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }

  // 🤖 METODOS ASISTENTE IA OLLAMA
  public toggleAiChat(): void {
    this.showAiChat = !this.showAiChat;
    this.cdr.detectChanges();
  }

  public enviarPreguntaRapida(pregunta: string): void {
    this.preguntaAi = pregunta;
    this.enviarConsultaIa();
  }

  public enviarConsultaIa(): void {
    const texto = this.preguntaAi.trim();
    if (!texto || this.isAiThinking) return;

    const hora = new Date().toLocaleTimeString('es-EC', { hour: '2-digit', minute: '2-digit' });

    this.aiMessages.push({
      sender: 'user',
      text: texto,
      time: hora
    });

    this.preguntaAi = '';
    this.isAiThinking = true;
    this.cdr.detectChanges();

    // La UI no puede quedar bloqueada si Ollama o la red tardan demasiado.
    this.http.post<any>(this.AI_URL, { pregunta: texto }).pipe(
      timeout(45_000)
    ).subscribe({
      next: (res) => {
        this.ngZone.run(() => {
          this.isAiThinking = false;
          let respTexto = res.respuesta || 'No se recibió respuesta del modelo local.';
          respTexto = respTexto.replace(/\n/g, '<br>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
          this.aiMessages.push({
            sender: 'ai',
            text: respTexto,
            time: new Date().toLocaleTimeString('es-EC', { hour: '2-digit', minute: '2-digit' })
          });
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.isAiThinking = false;
          this.aiMessages.push({
            sender: 'ai',
            text: 'No se pudo consultar el Copiloto Operativo. Verifica la conexión del sistema.',
            time: new Date().toLocaleTimeString('es-EC', { hour: '2-digit', minute: '2-digit' })
          });
          const ultimoMensaje = this.aiMessages[this.aiMessages.length - 1];
          ultimoMensaje.text = err?.name === 'TimeoutError'
            ? 'El Copiloto Operativo tardó demasiado en responder. Intenta nuevamente; los servicios continúan disponibles.'
            : 'No fue posible consultar el Copiloto Operativo en este momento. Verifica la conexión del sistema e inténtalo de nuevo.';
          this.cdr.detectChanges();
        });
      }
    });
  }
}
