import { Component, OnInit, OnDestroy, NgZone, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common'; 
import { HttpClient } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router'; 
import { FormsModule } from '@angular/forms';
import { WebsocketService } from '../../services/websocket';
import { AuthService } from '../../services/auth.service';
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

  private API_URL = 'http://localhost:8081/api/reportes';
  private clockInterval: any;
  private threadInterval: any;
  private metricsInterval: any;

  // CRUD Unidades
  public showUnidadesModal: boolean = false;
  public listaUnidades: any[] = [];
  public nuevaUnidad = { nombre: '', tipo: '' };
  private UNIDADES_API_URL = 'http://localhost:8081/api/unidades';

  // Reportes Finales (Bitácoras)
  public showReportesModal: boolean = false;
  public listaReportesFinales: any[] = [];

  // Historial Completo de Incidentes
  public showHistorialModal: boolean = false;
  public listaHistorialCompleto: any[] = [];

  // Usuario actual (se inicializa en el constructor)
  public currentUser$: any;

  constructor(
    private http: HttpClient, 
    private wsService: WebsocketService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private router: Router
  ) {
    this.currentUser$ = this.authService.getUser$();
  }

  ngOnInit(): void {
    this.iniciarMapa();
    this.cargarHistorial();
    this.iniciarReloj();
    this.iniciarMonitoreoHilos();
    this.iniciarMetricasSimuladas();

    this.wsService.escucharNuevosReportes().subscribe({
      next: (reporteRecibido) => {
        let reporteFormateado = typeof reporteRecibido === 'string' ? JSON.parse(reporteRecibido) : reporteRecibido;
        this.ngZone.run(() => {
          // Guardar el ID como nuevo para efecto visual
          this.newlyArrivedIds.add(reporteFormateado.id);
          setTimeout(() => {
            this.newlyArrivedIds.delete(reporteFormateado.id);
            this.cdr.detectChanges();
          }, 5000);

          this.listaReportes = [reporteFormateado, ...this.listaReportes]; 
          this.cdr.detectChanges(); 
        });
        this.agregarMarcador(reporteFormateado);
        this.selectReport(reporteFormateado);
      }
    });

    this.wsService.escucharUnidadesEstado().subscribe({
      next: (evento) => {
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
  }

  ngOnDestroy(): void {
    if (this.clockInterval) clearInterval(this.clockInterval);
    if (this.threadInterval) clearInterval(this.threadInterval);
    if (this.metricsInterval) clearInterval(this.metricsInterval);
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
    // Coordenadas de Santo Domingo, Ecuador [-0.253, -79.177]
    this.mapa = L.map('mapa-bomberos', {
      zoomControl: false // Ocultamos el zoom predeterminado para usar el diseño personalizado si es necesario
    }).setView([-0.253012, -79.177024], 13);

    // Mapa más claro y fluido para mejor lectura en UI/UX
    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains: 'abcd',
      maxZoom: 19,
      tileSize: 256,
      detectRetina: true
    }).addTo(this.mapa);

    // Añadir controles de zoom de Leaflet arriba a la derecha para no tapar la leyenda
    L.control.zoom({ position: 'topright' }).addTo(this.mapa);

    setTimeout(() => this.mapa.invalidateSize(), 500);
  }

  private cargarHistorial(): void {
    this.http.get<any[]>(this.API_URL).subscribe({
      next: (historial) => {
        this.ngZone.run(() => {
          // Filtrar los que ya fueron cerrados visualmente en el dashboard
          this.listaReportes = historial.reverse().filter(rep => !localStorage.getItem('closed_report_' + rep.id));
          this.cdr.detectChanges(); 
        });
        this.listaReportes.forEach(rep => this.agregarMarcador(rep));
      },
      error: (err) => console.error('Error cargando historial de reportes:', err)
    });
  }

  private agregarMarcador(rep: any): void {
    const classif = this.clasificarReporte(rep.descripcion);
    const lat = Number(rep.latitud);
    const lng = Number(rep.longitud);

    let ringBg = 'rgba(59,130,246,0.2)';
    if (classif.severity === 'critical') ringBg = 'rgba(255,59,59,0.3)';
    else if (classif.severity === 'high') ringBg = 'rgba(245,158,11,0.2)';

    // DivIcon mapping structure of the pin
    const shortTitle = classif.title.split(' ').slice(0, 2).join(' ');
    const pinIcon = L.divIcon({
      className: 'custom-leaflet-marker',
      html: `
        <div class="map-pin">
          <div class="google-gps-pin">
            <svg viewBox="0 0 24 24" width="60" height="60" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" fill="#ea4335" stroke="#ffffff" stroke-width="1.8"/>
            </svg>
          </div>
          <div class="pin-label">#${rep.id} · ${shortTitle}</div>
        </div>
      `,
      iconSize: [70, 62],
      iconAnchor: [35, 58],
      popupAnchor: [0, -45]
    });

    const marcador = L.marker([lat, lng], { icon: pinIcon }).addTo(this.mapa);
    marcador.bindPopup(`
      <div style="font-family:'Barlow',sans-serif; font-size:12px; color:#e2e8f0; background:#0f1218; border:1px solid #2a3348; padding:8px; border-radius:4px;">
        <b style="color:${classif.color}; font-family:'Barlow Condensed',sans-serif; font-size:13px;">${classif.icon} ${classif.title}</b><br>
        <span style="color:#94a3b8; font-size:10px;">ID: #${rep.id}</span><br>
        <p style="margin:5px 0; color:#cbd5e1; line-height:1.3;">${rep.descripcion}</p>
        <a href="/detalle/${rep.id}" style="color:#a78bfa; font-weight:600; text-decoration:none; display:inline-block; margin-top:4px;">Ver Detalles del Incidente &rarr;</a>
      </div>
    `, {
      closeButton: false,
      className: 'custom-popup'
    });

    this.markersMap.set(rep.id, marcador);
  }

  public selectReport(rep: any): void {
    const lat = Number(rep.latitud);
    const lng = Number(rep.longitud);
    this.mapa.flyTo([lat, lng], 15);
    
    setTimeout(() => {
      const marker = this.markersMap.get(rep.id);
      if (marker) {
        marker.openPopup();
      }
    }, 400);
  }

  public limpiarTodosLosIncidentes(): void {
    if (!this.listaReportes || this.listaReportes.length === 0) {
      alert('No hay incidentes activos para limpiar en el dashboard.');
      return;
    }

    if (confirm('¿Está seguro de que desea limpiar y dar por solucionados todos los incidentes activos del panel central?')) {
      // 1. Guardar todos en localStorage como cerrados
      this.listaReportes.forEach((rep) => {
        localStorage.setItem('closed_report_' + rep.id, 'true');
      });

      // 2. Quitar los marcadores del mapa
      this.markersMap.forEach((marker) => {
        this.mapa.removeLayer(marker);
      });
      this.markersMap.clear();

      // 3. Vaciar la lista local y actualizar la vista
      this.listaReportes = [];
      this.cdr.detectChanges();

      console.log('Dashboard central limpio.');
    }
  }

  // Visual helper methods mapping classification
  public clasificarReporte(desc: string): any {
    const text = (desc || '').toLowerCase();
    if (text.includes('incendio') || text.includes('fuego') || text.includes('quema') || text.includes('atrapad') || text.includes('llama')) {
      if (text.includes('crítico') || text.includes('3 pisos') || text.includes('edificio') || text.includes('casa') || text.includes('industrial') || text.includes('residencial')) {
        return {
          severity: 'critical',
          title: 'INCENDIO ESTRUCTURAL',
          badge: 'CRÍTICO',
          icon: '🔥',
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
        icon: '🌲',
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
          icon: '⚠️',
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
          icon: '🏗',
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
        icon: '☣️',
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
          icon: '💧',
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
        icon: '🚗',
        color: '#60a5fa',
        aiTags: ['Colisión múltiple', 'Obstrucción vía'],
        conf: '77%',
        mainTag: 'Rescate necesario'
      };
    }
    // Default
    return {
      severity: 'medium',
      title: 'REPORTE CIUDADANO',
      badge: 'MEDIO',
      icon: '📍',
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
    // Core threads showing as green/active, other busy threads as amber, rest empty
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

  // --- MÉTODOS CRUD DE UNIDADES ---

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
    if (!this.nuevaUnidad.nombre || !this.nuevaUnidad.tipo) {
      alert('Por favor ingrese el nombre y tipo de la unidad.');
      return;
    }
    this.http.post<any>(this.UNIDADES_API_URL, this.nuevaUnidad).subscribe({
      next: () => {
        this.nuevaUnidad = { nombre: '', tipo: '' };
        this.cargarUnidades();
      },
      error: (err) => console.error('Error agregando unidad:', err)
    });
  }

  public eliminarUnidad(id: number): void {
    if (confirm('¿Seguro que deseas eliminar esta unidad permanentemente?')) {
      this.http.delete(`${this.UNIDADES_API_URL}/${id}`).subscribe({
        next: () => this.cargarUnidades(),
        error: (err) => console.error('Error eliminando unidad:', err)
      });
    }
  }

  public forzarEstado(id: number, event: any): void {
    const nuevoEstado = event.target.value;
    this.http.put(`${this.UNIDADES_API_URL}/${id}/estado?estado=${nuevoEstado}`, {}).subscribe({
      next: () => this.cargarUnidades(),
      error: (err) => alert('Error al cambiar estado: ' + err.message)
    });
  }

  // ─── LÓGICA DE REPORTES FINALES (BITÁCORAS) ───
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

  // ─── LÓGICA DE HISTORIAL DE INCIDENTES CIUDADANOS ───
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

  public logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }
}