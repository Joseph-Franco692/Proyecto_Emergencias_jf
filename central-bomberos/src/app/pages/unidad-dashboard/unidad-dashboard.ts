import { Component, OnInit, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { WebsocketService } from '../../services/websocket';
import { Subscription } from 'rxjs';
import * as L from 'leaflet';

@Component({
  selector: 'app-unidad-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './unidad-dashboard.html',
  styleUrls: ['./unidad-dashboard.css']
})
export class UnidadDashboardComponent implements OnInit, OnDestroy {

  // ─── FASES ────────────────────────────────────────────────────────────────
  public fase: 'seleccion' | 'espera' | 'ruta' = 'seleccion';

  // ─── DATOS DE UNIDAD ──────────────────────────────────────────────────────
  public todasLasUnidades: any[] = [];
  public unidadActual: any = null;
  public isLoadingUnidades: boolean = true;

  // ─── DATOS DE EMERGENCIA ─────────────────────────────────────────────────
  public emergenciaActual: any = null;
  public tiempoEnRuta: string = '00:00';
  public distanciaKm: string = '...';
  public distanciaRutaKm: string = '...';
  public duracionEstimada: string = '...';
  public isFinalizando: boolean = false;
  public finalizadoMensaje: string = '';

  // ─── INSTRUCCIONES DE RUTA ───────────────────────────────────────────────
  public instruccionActual: string = '📍 Calculando ruta...';
  public instruccionIcono: string = '➡️';
  public isCalculandoRuta: boolean = false;
  public rutaCalculada: boolean = false;

  // ─── MAPA LEAFLET ─────────────────────────────────────────────────────────
  private mapa!: L.Map;
  private marcadorUnidad!: L.Marker;
  private marcadorIncidente!: L.Marker;
  private rutaPolyline!: L.Polyline;
  private watchId: number | null = null;
  public latitudUnidad: number = -0.253012;
  public longitudUnidad: number = -79.177024;

  // ─── RUTA POR CALLES (OSRM) ───────────────────────────────────────────────
  // OSRM es el motor de rutas de OpenStreetMap — 100% gratuito, sin API key
  private readonly OSRM_URL = 'https://router.project-osrm.org/route/v1/driving';
  private coordenadasRuta: [number, number][] = [];
  private pasoActualIndex: number = 0;
  private pasosRuta: any[] = [];

  private readonly API_URL = 'http://localhost:8081/api/reportes';
  private readonly UNIDADES_URL = 'http://localhost:8081/api/unidades';
  private wsSub!: Subscription;
  private timerInterval: any;
  private timerSeconds: number = 0;
  private recalcInterval: any; // recalcula la ruta cada 15s mientras el GPS avanza

  constructor(
    private http: HttpClient,
    private wsService: WebsocketService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.cargarUnidades();
    this.iniciarEscuchaWebSocket();
    this.iniciarGPS();
  }

  ngOnDestroy(): void {
    if (this.wsSub) this.wsSub.unsubscribe();
    if (this.timerInterval) clearInterval(this.timerInterval);
    if (this.recalcInterval) clearInterval(this.recalcInterval);
    if (this.watchId !== null) navigator.geolocation.clearWatch(this.watchId);
    if (this.mapa) this.mapa.remove();
  }

  // ─── CARGA DE UNIDADES ────────────────────────────────────────────────────

  private cargarUnidades(): void {
    this.http.get<any[]>(this.UNIDADES_URL).subscribe({
      next: (unidades) => {
        this.ngZone.run(() => {
          this.todasLasUnidades = unidades;
          this.isLoadingUnidades = false;
          this.cdr.detectChanges();
        });
      },
      error: () => {
        this.ngZone.run(() => {
          this.isLoadingUnidades = false;
          this.cdr.detectChanges();
        });
      }
    });
  }

  // ─── UI MODALS ───
  public showOperadorModal: boolean = false;
  public showFinalizarModal: boolean = false;
  public formOperador: string = '';
  public unidadASeleccionar: any = null;
  public formPersonal: string = '';
  public formNovedades: string = '';

  public intentarSeleccionarUnidad(unidad: any): void {
    this.unidadASeleccionar = unidad;
    this.formOperador = '';
    this.showOperadorModal = true;
    this.cdr.detectChanges();
  }

  public confirmarSeleccionOperador(): void {
    if (!this.formOperador || this.formOperador.trim() === '') {
       alert('Debe ingresar el nombre del operador para poder registrar la unidad.');
       return;
    }
    
    this.unidadASeleccionar.operador = this.formOperador.trim();
    this.unidadActual = this.unidadASeleccionar;
    this.fase = 'espera';
    this.showOperadorModal = false;
    this.cdr.detectChanges();
  }

  public cancelarSeleccionOperador(): void {
    this.showOperadorModal = false;
    this.unidadASeleccionar = null;
    this.cdr.detectChanges();
  }

  // ─── GPS EN TIEMPO REAL ───────────────────────────────────────────────────

  private iniciarGPS(): void {
    if (!navigator.geolocation) return;
    this.watchId = navigator.geolocation.watchPosition(
      (pos) => {
        this.ngZone.run(() => {
          const latAnterior = this.latitudUnidad;
          const lngAnterior = this.longitudUnidad;
          this.latitudUnidad = pos.coords.latitude;
          this.longitudUnidad = pos.coords.longitude;

          // Solo actualizamos mapa si hay un desplazamiento real (>5m)
          const movimiento = this.calcularDistanciaKm(latAnterior, lngAnterior, this.latitudUnidad, this.longitudUnidad);
          if (movimiento > 0.005 || !latAnterior) {
            this.actualizarPosicionEnMapa();
          }
          this.cdr.detectChanges();
        });
      },
      (err) => console.warn('GPS error:', err),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 3000 }
    );
  }

  private actualizarPosicionEnMapa(): void {
    if (!this.mapa || !this.marcadorUnidad) return;

    const pos = new L.LatLng(this.latitudUnidad, this.longitudUnidad);
    this.marcadorUnidad.setLatLng(pos);

    // Seguir el marcador de la unidad en la pantalla (map follow)
    this.mapa.panTo(pos, { animate: true, duration: 0.8 });

    if (this.emergenciaActual) {
      // Actualizar distancia en línea recta
      const dist = this.calcularDistanciaKm(
        this.latitudUnidad, this.longitudUnidad,
        Number(this.emergenciaActual.latitud), Number(this.emergenciaActual.longitud)
      );
      this.distanciaKm = dist.toFixed(2);

      // Determinar la instrucción de giro actual según posición en la ruta
      this.actualizarInstruccionActual();

      // Si nos alejamos mucho de la ruta calculada (>100m), recalcular
      if (this.rutaCalculada && this.coordenadasRuta.length > 0) {
        const distARuta = this.distanciaMinAPolyline();
        if (distARuta > 0.08) { // 80 metros fuera de ruta
          console.log('🔄 Recalculando ruta (desvío detectado)...');
          this.calcularRutaOSRM();
        }
      }
    }
  }

  // ─── WEBSOCKET ────────────────────────────────────────────────────────────

  private iniciarEscuchaWebSocket(): void {
    this.wsSub = this.wsService.escucharUnidadesEstado().subscribe({
      next: (evento) => {
        this.ngZone.run(() => {
          if (evento?.tipo === 'DESPACHO') {
            this.procesarEventoDespacho(evento);
          } else if (evento?.tipo === 'LIBERACION') {
            if (String(evento.unidad?.id) === String(this.unidadActual?.id)) {
              this.procesarLiberacion();
            }
          }
          this.cdr.detectChanges();
        });
      }
    });
  }

  private procesarEventoDespacho(evento: any): void {
    if (!this.unidadActual) return;
    const mismaUnidad = evento.unidades?.find(
      (u: any) => String(u.id) === String(this.unidadActual.id)
    );
    if (!mismaUnidad) return;

    this.emergenciaActual = {
      reporteId: evento.reporteId,
      latitud: evento.latitud,
      longitud: evento.longitud,
      descripcion: evento.descripcion,
      celular: evento.celularReportero
    };

    this.unidadActual = { ...this.unidadActual, estado: 'EN_RUTA' };
    this.fase = 'ruta';
    this.iniciarTimerRuta();
    this.cdr.detectChanges();

    setTimeout(() => {
      this.inicializarMapa();
      // Después de inicializar el mapa, calcular ruta real por calles
      setTimeout(() => this.calcularRutaOSRM(), 400);
    }, 250);
  }

  private procesarLiberacion(): void {
    this.fase = 'espera';
    this.emergenciaActual = null;
    this.finalizadoMensaje = '';
    this.isFinalizando = false;
    this.rutaCalculada = false;
    this.coordenadasRuta = [];
    this.pasosRuta = [];
    this.instruccionActual = '📍 Calculando ruta...';
    if (this.timerInterval) clearInterval(this.timerInterval);
    if (this.recalcInterval) clearInterval(this.recalcInterval);
    this.timerSeconds = 0;
    this.tiempoEnRuta = '00:00';
    if (this.mapa) {
      this.mapa.remove();
      (this.mapa as any) = null;
    }
    if (this.unidadActual) {
      this.unidadActual = { ...this.unidadActual, estado: 'DISPONIBLE' };
    }
    this.cdr.detectChanges();
  }

  // ─── MAPA LEAFLET ─────────────────────────────────────────────────────────

  private inicializarMapa(): void {
    if (this.mapa) this.mapa.remove();

    const lat = this.latitudUnidad;
    const lng = this.longitudUnidad;
    const latInc = Number(this.emergenciaActual.latitud);
    const lngInc = Number(this.emergenciaActual.longitud);

    // Mapa oscuro estilo navegación GPS
    this.mapa = L.map('mapa-unidad', {
      zoomControl: false,
      attributionControl: false
    }).setView([lat, lng], 15);

    // Tiles oscuros estilo navegación (CartoDB Dark Matter)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      subdomains: 'abcd',
      maxZoom: 20
    }).addTo(this.mapa);

    // Zoom control en posición inferior derecha
    L.control.zoom({ position: 'bottomright' }).addTo(this.mapa);

    // Marcador UNIDAD — círculo rojo con 🚒
    const iconoUnidad = L.divIcon({
      className: '',
      html: `<div style="
        background: linear-gradient(135deg,#ff3b3b,#dc2626);
        border: 3px solid #fff;
        border-radius: 50%;
        width: 44px; height: 44px;
        display: flex; align-items:center; justify-content:center;
        font-size: 22px;
        box-shadow: 0 0 0 6px rgba(255,59,59,0.3), 0 4px 14px rgba(0,0,0,0.5);
        animation: gps-pulse 2s ease-in-out infinite;
      ">🚒</div>`,
      iconSize: [44, 44],
      iconAnchor: [22, 22]
    });

    this.marcadorUnidad = L.marker([lat, lng], { icon: iconoUnidad, zIndexOffset: 100 })
      .addTo(this.mapa)
      .bindTooltip('📍 Tu posición', { permanent: false, direction: 'top' });

    // Marcador INCIDENTE — pin grande rojo pulsante
    const iconoIncidente = L.divIcon({
      className: '',
      html: `<div style="position:relative; display:flex; align-items:center; justify-content:center;">
        <div style="
          position:absolute;
          width:60px; height:60px;
          border-radius:50%;
          background: rgba(255,59,59,0.3);
          animation: gps-pulse 1.5s ease-in-out infinite;
          top:50%; left:50%; transform:translate(-50%,-50%);
        "></div>
        <svg viewBox="0 0 24 24" width="52" height="52">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" fill="#ff3b3b" stroke="#fff" stroke-width="1.5"/>
        </svg>
      </div>`,
      iconSize: [52, 52],
      iconAnchor: [26, 52]
    });

    this.marcadorIncidente = L.marker([latInc, lngInc], { icon: iconoIncidente })
      .addTo(this.mapa)
      .bindTooltip(`🚨 Emergencia #${this.emergenciaActual.reporteId}`, { permanent: true, direction: 'top' });

    // Polyline provisional (línea recta) mientras se calcula la ruta real
    this.rutaPolyline = L.polyline([[lat, lng], [latInc, lngInc]], {
      color: '#ff3b3b',
      weight: 4,
      opacity: 0.5,
      dashArray: '10, 8'
    }).addTo(this.mapa);

    const grupo = L.featureGroup([this.marcadorUnidad, this.marcadorIncidente]);
    this.mapa.fitBounds(grupo.getBounds().pad(0.25));

    this.distanciaKm = this.calcularDistanciaKm(lat, lng, latInc, lngInc).toFixed(2);

    setTimeout(() => this.mapa.invalidateSize(), 300);
  }

  // ─── ROUTING OSRM (CALLES REALES) ─────────────────────────────────────────

  /**
   * Llama a la API pública de OSRM para obtener la ruta real por calles.
   * OSRM es el motor de routing de OpenStreetMap — 100% gratuito, sin API key.
   * Endpoint: https://router.project-osrm.org/route/v1/driving/{lng},{lat};{lng},{lat}
   */
  public calcularRutaOSRM(): void {
    if (!this.emergenciaActual) return;

    this.isCalculandoRuta = true;
    this.instruccionActual = '🔄 Calculando ruta por calles...';
    this.instruccionIcono = '🔄';
    this.cdr.detectChanges();

    const lat1 = this.latitudUnidad;
    const lng1 = this.longitudUnidad;
    const lat2 = Number(this.emergenciaActual.latitud);
    const lng2 = Number(this.emergenciaActual.longitud);

    // OSRM usa formato: lng,lat (invertido respecto a Leaflet)
    const url = `${this.OSRM_URL}/${lng1},${lat1};${lng2},${lat2}?overview=full&geometries=geojson&steps=true&annotations=false`;

    this.http.get<any>(url).subscribe({
      next: (respuesta) => {
        this.ngZone.run(() => {
          this.isCalculandoRuta = false;

          if (respuesta.routes && respuesta.routes.length > 0) {
            const ruta = respuesta.routes[0];

            // ── Extraer coordenadas de la geometría GeoJSON ──
            // OSRM retorna [lng, lat], Leaflet necesita [lat, lng]
            this.coordenadasRuta = ruta.geometry.coordinates.map(
              (coord: [number, number]) => [coord[1], coord[0]] as [number, number]
            );

            // ── Extraer pasos/instrucciones de giro ──
            this.pasosRuta = [];
            if (ruta.legs && ruta.legs.length > 0) {
              ruta.legs[0].steps.forEach((paso: any) => {
                this.pasosRuta.push({
                  instruccion: this.traducirManeuver(paso.maneuver),
                  icono: this.iconoManeuver(paso.maneuver),
                  nombre: paso.name || '',
                  distancia: paso.distance,
                  ubicacion: paso.maneuver.location // [lng, lat]
                });
              });
            }

            // ── Actualizar distancia y tiempo estimado ──
            this.distanciaRutaKm = (ruta.distance / 1000).toFixed(1);
            const minutos = Math.ceil(ruta.duration / 60);
            this.duracionEstimada = minutos < 60
              ? `${minutos} min`
              : `${Math.floor(minutos / 60)}h ${minutos % 60}min`;

            // ── Dibujar la ruta en el mapa (reemplaza la línea recta) ──
            if (this.rutaPolyline) {
              this.mapa.removeLayer(this.rutaPolyline);
            }
            this.rutaPolyline = L.polyline(this.coordenadasRuta, {
              color: '#ff3b3b',
              weight: 6,
              opacity: 0.95,
              lineJoin: 'round',
              lineCap: 'round'
            }).addTo(this.mapa);

            // Sombra debajo de la ruta para efecto Google Maps
            L.polyline(this.coordenadasRuta, {
              color: 'rgba(0,0,0,0.4)',
              weight: 9,
              opacity: 0.5,
              lineJoin: 'round',
              lineCap: 'round'
            }).addTo(this.mapa).bringToBack();

            // Ajustar vista para mostrar la ruta completa
            this.mapa.fitBounds(this.rutaPolyline.getBounds().pad(0.15));

            this.rutaCalculada = true;
            this.pasoActualIndex = 0;
            this.actualizarInstruccionActual();

            // Recalcular automáticamente cada 20 segundos mientras se mueve
            if (this.recalcInterval) clearInterval(this.recalcInterval);
            this.recalcInterval = setInterval(() => {
              if (this.fase === 'ruta' && this.emergenciaActual) {
                this.calcularRutaOSRM();
              }
            }, 20000);

            console.log(`✅ Ruta calculada: ${this.distanciaRutaKm} km · ${this.duracionEstimada}`);
          } else {
            this.instruccionActual = '⚠️ No se encontró ruta vial. Navega en línea recta.';
            this.instruccionIcono = '⚠️';
          }
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        console.warn('Error OSRM:', err);
        this.ngZone.run(() => {
          this.isCalculandoRuta = false;
          this.instruccionActual = '⚠️ Sin conexión al servidor de rutas. Modo línea recta activo.';
          this.instruccionIcono = '⚠️';
          this.cdr.detectChanges();
        });
      }
    });
  }

  /**
   * Determina la instrucción de giro más cercana a la posición actual.
   */
  private actualizarInstruccionActual(): void {
    if (!this.pasosRuta || this.pasosRuta.length === 0) return;

    let minDist = Infinity;
    let pasoMasCercano = 0;

    this.pasosRuta.forEach((paso, i) => {
      if (!paso.ubicacion) return;
      const dist = this.calcularDistanciaKm(
        this.latitudUnidad, this.longitudUnidad,
        paso.ubicacion[1], paso.ubicacion[0] // ubicacion es [lng, lat]
      );
      if (dist < minDist) {
        minDist = dist;
        pasoMasCercano = i;
      }
    });

    // Mostrar el paso actual o el siguiente si ya pasamos este punto
    const pasoIdx = Math.min(pasoMasCercano + 1, this.pasosRuta.length - 1);
    const paso = this.pasosRuta[pasoIdx];
    if (paso) {
      this.instruccionIcono = paso.icono;
      const distPaso = (minDist * 1000).toFixed(0);
      this.instruccionActual = distPaso + 'm — ' + paso.instruccion + (paso.nombre ? ` por ${paso.nombre}` : '');
    }
  }

  /**
   * Calcula la distancia mínima desde la posición actual a la polyline de ruta.
   * Usado para detectar desviaciones y recalcular.
   */
  private distanciaMinAPolyline(): number {
    if (!this.coordenadasRuta.length) return 0;
    let min = Infinity;
    this.coordenadasRuta.forEach(coord => {
      const d = this.calcularDistanciaKm(this.latitudUnidad, this.longitudUnidad, coord[0], coord[1]);
      if (d < min) min = d;
    });
    return min;
  }

  // ─── TRADUCCIÓN DE INSTRUCCIONES OSRM ────────────────────────────────────

  private traducirManeuver(maneuver: any): string {
    if (!maneuver) return 'Continúa';
    const tipo = maneuver.type || '';
    const modificador = maneuver.modifier || '';

    const mapa: { [k: string]: string } = {
      'depart':                  'Comienza la ruta',
      'arrive':                  '🚨 Llegaste al destino',
      'turn left':               'Gira a la izquierda',
      'turn right':              'Gira a la derecha',
      'turn slight left':        'Gira ligeramente a la izquierda',
      'turn slight right':       'Gira ligeramente a la derecha',
      'turn sharp left':         'Gira fuertemente a la izquierda',
      'turn sharp right':        'Gira fuertemente a la derecha',
      'turn uturn':              'Da la vuelta',
      'merge left':              'Incorpora a la izquierda',
      'merge right':             'Incorpora a la derecha',
      'merge':                   'Incorpora a la vía',
      'ramp left':               'Toma la salida a la izquierda',
      'ramp right':              'Toma la salida a la derecha',
      'fork left':               'Toma la bifurcación izquierda',
      'fork right':              'Toma la bifurcación derecha',
      'end of road left':        'Al final de la vía, gira a la izquierda',
      'end of road right':       'Al final de la vía, gira a la derecha',
      'continue straight':       'Continúa recto',
      'roundabout':              'Ingresa a la rotonda',
      'exit roundabout':         'Sale de la rotonda',
      'rotary':                  'Ingresa a la rotonda',
      'exit rotary':             'Sale de la rotonda',
    };

    const clave = `${tipo} ${modificador}`.trim();
    return mapa[clave] || mapa[tipo] || 'Continúa';
  }

  private iconoManeuver(maneuver: any): string {
    if (!maneuver) return '➡️';
    const tipo = maneuver.type || '';
    const mod = maneuver.modifier || '';

    if (tipo === 'arrive') return '🚨';
    if (tipo === 'depart') return '🚀';
    if (mod.includes('left') && mod.includes('sharp')) return '↩️';
    if (mod.includes('right') && mod.includes('sharp')) return '↪️';
    if (mod.includes('left') && mod.includes('slight')) return '↖️';
    if (mod.includes('right') && mod.includes('slight')) return '↗️';
    if (mod === 'left' || mod.includes('left')) return '⬅️';
    if (mod === 'right' || mod.includes('right')) return '➡️';
    if (mod === 'uturn') return '🔄';
    if (tipo === 'roundabout' || tipo === 'rotary') return '🔵';
    if (tipo === 'merge') return '🔀';
    return '⬆️';
  }

  // ─── TIMER DE RUTA ────────────────────────────────────────────────────────

  private iniciarTimerRuta(): void {
    if (this.timerInterval) clearInterval(this.timerInterval);
    this.timerSeconds = 0;
    this.timerInterval = setInterval(() => {
      this.ngZone.run(() => {
        this.timerSeconds++;
        const min = Math.floor(this.timerSeconds / 60).toString().padStart(2, '0');
        const sec = (this.timerSeconds % 60).toString().padStart(2, '0');
        this.tiempoEnRuta = `${min}:${sec}`;
        this.cdr.detectChanges();
      });
    }, 1000);
  }

  // ─── ACCIONES DEL OPERADOR ────────────────────────────────────────────────

  public marcarLlegada(): void {
    if (!this.unidadActual) return;
    this.http.put<any>(`${this.UNIDADES_URL}/${this.unidadActual.id}/en-sitio`, {}).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.unidadActual = { ...this.unidadActual, estado: 'EN_SITIO' };
          this.instruccionActual = '✅ Llegaste al destino. Procede con el protocolo de emergencia.';
          this.instruccionIcono = '✅';
          if (this.recalcInterval) clearInterval(this.recalcInterval);
          this.cdr.detectChanges();
        });
      },
      error: (err) => console.error('Error marcando llegada:', err)
    });
  }

  public abrirFinalizarModal(): void {
    if (!this.unidadActual || this.isFinalizando) return;
    this.formPersonal = '';
    this.formNovedades = '';
    this.showFinalizarModal = true;
    this.cdr.detectChanges();
  }

  public cancelarFinalizarEmergencia(): void {
    this.showFinalizarModal = false;
    this.cdr.detectChanges();
  }

  public confirmarFinalizarEmergencia(): void {
    if (!this.formPersonal || this.formPersonal.trim() === '') {
       alert('Es obligatorio reportar los nombres del personal involucrado para finalizar.');
       return;
    }
    if (!this.formNovedades || this.formNovedades.trim() === '') {
       alert('Es obligatorio reportar las novedades para finalizar.');
       return;
    }

    this.isFinalizando = true;
    this.showFinalizarModal = false;
    this.cdr.detectChanges();

    const payload = {
      operador: this.unidadActual.operador || 'No registrado',
      personal: this.formPersonal.trim(),
      novedades: this.formNovedades.trim()
    };

    this.http.put<any>(`${this.UNIDADES_URL}/${this.unidadActual.id}/disponibilizar`, payload).subscribe({
      next: (resp) => {
        this.ngZone.run(() => {
          this.isFinalizando = false;
          this.finalizadoMensaje = resp.reporteCerrado
            ? '✓ Emergencia cerrada. Todas las unidades se retiraron.'
            : '✓ Unidad disponible. Emergencia continúa con otras unidades.';
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.isFinalizando = false;
          this.finalizadoMensaje = '❌ Error al finalizar: ' + (err.error?.error || err.message);
          this.cdr.detectChanges();
        });
      }
    });
  }

  public abrirEnGoogleMaps(): void {
    if (!this.emergenciaActual) return;
    const lat = this.emergenciaActual.latitud;
    const lng = this.emergenciaActual.longitud;
    window.open(
      `https://www.google.com/maps/dir/?api=1&origin=${this.latitudUnidad},${this.longitudUnidad}&destination=${lat},${lng}&travelmode=driving`,
      '_blank'
    );
  }

  // ─── HELPERS ──────────────────────────────────────────────────────────────

  private calcularDistanciaKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371;
    const dLat = this.toRad(lat2 - lat1);
    const dLng = this.toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 +
              Math.cos(this.toRad(lat1)) * Math.cos(this.toRad(lat2)) * Math.sin(dLng / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private toRad(val: number): number {
    return val * (Math.PI / 180);
  }

  public getEstadoColor(): string {
    if (!this.unidadActual) return '#475569';
    if (this.unidadActual.estado === 'DISPONIBLE') return '#22c55e';
    if (this.unidadActual.estado === 'EN_RUTA') return '#f59e0b';
    if (this.unidadActual.estado === 'EN_SITIO') return '#3b82f6';
    return '#475569';
  }

  public getEstadoLabel(): string {
    if (!this.unidadActual) return '—';
    const estadoMap: { [k: string]: string } = {
      DISPONIBLE: 'DISPONIBLE',
      EN_RUTA: 'EN RUTA',
      EN_SITIO: 'EN SITIO'
    };
    return estadoMap[this.unidadActual.estado] || this.unidadActual.estado;
  }

  public truncar(texto: string, max: number): string {
    if (!texto) return '';
    return texto.length > max ? texto.substring(0, max) + '...' : texto;
  }
}
