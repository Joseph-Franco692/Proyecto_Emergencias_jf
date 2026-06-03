import { Component, OnInit, NgZone, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common'; 
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router'; // <-- Importante para usar routerLink
import { WebsocketService } from '../../services/websocket';
import * as L from 'leaflet';

const iconoRojo = L.icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-black.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
  iconSize: [25, 41], 
  iconAnchor: [12, 41], 
  popupAnchor: [1, -34], 
  shadowSize: [41, 41]
});

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule], 
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class DashboardComponent implements OnInit { 
  public listaReportes: any[] = [];
  private mapa!: L.Map;
  private API_URL = 'http://localhost:8081/api/reportes';

  constructor(
    private http: HttpClient, 
    private wsService: WebsocketService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.iniciarMapa();
    this.cargarHistorial();

    this.wsService.escucharNuevosReportes().subscribe({
      next: (reporteRecibido) => {
        let reporteFormateado = typeof reporteRecibido === 'string' ? JSON.parse(reporteRecibido) : reporteRecibido;
        this.ngZone.run(() => {
          this.listaReportes = [reporteFormateado, ...this.listaReportes]; 
          this.cdr.detectChanges(); 
        });
        this.agregarMarcador(reporteFormateado.latitud, reporteFormateado.longitud, reporteFormateado.descripcion);
      }
    });
  }

  private iniciarMapa(): void {
    this.mapa = L.map('mapa-bomberos').setView([-0.180653, -78.467834], 12);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.mapa);
    setTimeout(() => this.mapa.invalidateSize(), 500);
  }

  private agregarMarcador(lat: number, lng: number, popupTexto: string): void{
    const marcador = L.marker([lat, lng], {icon:iconoRojo}).addTo(this.mapa);
    marcador.bindPopup(`<b>Emergencia:</b><br>${popupTexto}`);
    this.mapa.flyTo([lat, lng], 15);
  }

  private cargarHistorial(): void {
    this.http.get<any[]>(this.API_URL).subscribe({
      next: (historial) => {
        this.ngZone.run(() => {
          this.listaReportes = historial.reverse();
          this.cdr.detectChanges(); 
        });
        this.listaReportes.forEach(rep => this.agregarMarcador(rep.latitud, rep.longitud, rep.descripcion));
      }
    });
  }
}