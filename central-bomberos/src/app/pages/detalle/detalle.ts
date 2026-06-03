import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-detalle',
  standalone: true,
  imports: [CommonModule, RouterModule], 
  templateUrl: './detalle.html',
  styleUrls: ['./detalle.css']
})
export class DetalleComponent implements OnInit {
  public reporteSeleccionado: any = null;
  public idIncidente: string | null = null;
  
  // Tu API del backend
  private API_URL = 'http://localhost:8081/api/reportes';

  constructor(
    private rutaActiva: ActivatedRoute,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    // 1. Angular lee el ID que viene en la URL
    this.idIncidente = this.rutaActiva.snapshot.paramMap.get('id');
    
    // 2. Si el ID existe, hacemos una petición HTTP GET para traer solo ese reporte
    if (this.idIncidente) {
      this.http.get<any>(`${this.API_URL}/${this.idIncidente}`).subscribe({
        next: (datos) => {
          this.reporteSeleccionado = datos;
          console.log('Datos cargados desde Spring Boot:', this.reporteSeleccionado);
        },
        error: (err) => console.error('Error al cargar el reporte', err)
      });
    }
  }
}