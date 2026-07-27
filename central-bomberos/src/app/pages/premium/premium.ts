import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { finalize, timeout } from 'rxjs/operators';

declare global {
  interface Window { paypal?: any; }
}

interface PlanConfig {
  nombre: string;
  montoCentavos: number;
  moneda: string;
  paypalClientId: string;
  plazoInstalacionDias: number;
}

interface PremiumOrder {
  codigoOrden: string;
  montoCentavos: number;
  moneda: string;
  proveedorPago: 'PAYPAL' | 'PAYPHONE';
  estadoPago: string;
  estadoInstalacion: string;
  proveedorOrdenId: string;
  urlPago?: string;
  fechaPago?: string;
  fechaLimiteInstalacion?: string;
  mensaje: string;
}

@Component({
  selector: 'app-premium',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './premium.html',
  styleUrl: './premium.css'
})
export class PremiumComponent implements OnInit, OnDestroy {
  private readonly apiUrl = 'http://localhost:8081/api/premium';

  plan: PlanConfig = {
    nombre: 'Plan Premium de Prevención', montoCentavos: 4999, moneda: 'USD',
    paypalClientId: '', plazoInstalacionDias: 2
  };
  planLoaded = false;
  step = 1;
  loading = false;
  error = '';
  order?: PremiumOrder;

  form = {
    nombres: '',
    email: '',
    telefono: '',
    identificacion: '',
    tipoEstablecimiento: 'VIVIENDA',
    nombreEstablecimiento: '',
    direccion: '',
    ciudad: 'Santo Domingo',
    provincia: 'Santo Domingo de los Tsáchilas',
    referencia: '',
    latitud: null as number | null,
    longitud: null as number | null,
    proveedorPago: 'PAYPAL' as const
  };

  constructor(
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.cargarPlan();
    this.detectarUbicacion();
  }

  ngOnDestroy(): void {}

  get precio(): string {
    return (this.plan.montoCentavos / 100).toFixed(2);
  }

  solicitarInstalacion(): void {
    this.step = 2;
    this.error = '';
    if (!this.planLoaded) this.cargarPlan();
    setTimeout(() => document.querySelector('.checkout')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    }), 0);
  }

  continuar(): void {
    this.error = '';
    if (!this.form.nombres.trim() || !this.form.email.trim() || !this.form.telefono.trim()
        || !this.form.direccion.trim() || !this.form.ciudad.trim() || !this.form.provincia.trim()) {
      this.error = 'Completa todos los campos obligatorios para continuar.';
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.form.email)) {
      this.error = 'Ingresa un correo electrónico válido.';
      return;
    }
    if (!/^\d{10,15}$/.test(this.form.telefono.replace(/\D/g, ''))) {
      this.error = 'Ingresa un teléfono válido de 10 a 15 dígitos.';
      return;
    }
    this.step = 3;
  }

  crearOrden(): void {
    if (this.loading) return;
    if (!this.planLoaded || !this.plan.paypalClientId) {
      this.loading = true;
      this.error = '';
      this.cargarPlan(() => {
        this.loading = false;
        this.crearOrden();
      }, () => {
        this.loading = false;
        this.error = 'No se pudo conectar con Spring Boot para preparar PayPal. Verifica que el backend esté activo en el puerto 8081.';
      });
      return;
    }
    this.loading = true;
    this.error = '';
    this.http.post<PremiumOrder>(`${this.apiUrl}/ordenes`, this.form).pipe(
      timeout(20000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: order => {
        this.order = order;
        this.step = 4;
        this.cdr.detectChanges();
        setTimeout(() => this.mostrarPaypal(), 0);
      },
      error: err => {
        this.error = err.name === 'TimeoutError'
          ? 'PayPal tardó demasiado en responder. Intenta nuevamente; no se realizó ningún cobro.'
          : (err.error?.message || 'No se pudo crear la orden de pago.');
        this.cdr.detectChanges();
      }
    });
  }

  nuevaSolicitud(): void {
    this.order = undefined;
    this.error = '';
    this.step = 1;
  }

  private mostrarPaypal(): void {
    if (!this.plan || !this.order) return;
    this.cargarPaypalSdk(this.plan.paypalClientId).then(() => {
      if (!window.paypal || !this.order) throw new Error('No se pudo inicializar PayPal');
      const container = document.getElementById('paypal-button-container');
      if (container) container.innerHTML = '';
      return window.paypal.Buttons({
        style: { layout: 'vertical', shape: 'rect', label: 'paypal', height: 48 },
        createOrder: () => this.order!.proveedorOrdenId,
        onApprove: (data: any) => this.capturarPaypal(data.orderID),
        onCancel: () => {
          this.error = 'El pago fue cancelado. Puedes intentarlo nuevamente.';
          this.cdr.detectChanges();
        },
        onError: () => {
          this.reconciliarPaypal();
        }
      }).render('#paypal-button-container');
    }).catch(() => {
      this.error = 'No se pudo cargar el botón seguro de PayPal.';
      this.cdr.detectChanges();
    });
  }

  private capturarPaypal(paypalOrderId: string): Promise<void> {
    if (!this.order) return Promise.reject();
    this.loading = true;
    return new Promise((resolve, reject) => {
      this.http.post<PremiumOrder>(
        `${this.apiUrl}/ordenes/${this.order!.codigoOrden}/paypal/capturar`,
        { paypalOrderId }
      ).subscribe({
        next: order => {
          this.order = order;
          this.loading = false;
          this.mostrarConfirmacion();
          this.cdr.detectChanges();
          resolve();
        },
        error: err => {
          this.loading = false;
          this.error = err.error?.message || 'No se pudo confirmar el pago con PayPal.';
          this.cdr.detectChanges();
          reject(err);
        }
      });
    });
  }

  reconciliarPaypal(): void {
    if (!this.order) {
      this.error = 'PayPal no pudo procesar el pago.';
      this.cdr.detectChanges();
      return;
    }
    this.loading = true;
    this.http.post<PremiumOrder>(
      `${this.apiUrl}/ordenes/${this.order.codigoOrden}/paypal/reconciliar`,
      {},
      { params: { email: this.form.email } }
    ).pipe(
      timeout(15000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: order => {
        this.order = order;
        this.error = '';
        this.mostrarConfirmacion();
        this.cdr.detectChanges();
      },
      error: err => {
        if (!this.error) {
          this.error = err.error?.message || 'PayPal no confirmó el pago. Puedes intentarlo nuevamente.';
        }
        this.cdr.detectChanges();
      }
    });
  }

  private mostrarConfirmacion(): void {
    this.step = 5;
  }

  private cargarPaypalSdk(clientId: string): Promise<void> {
    if (window.paypal) return Promise.resolve();
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=USD&intent=capture`;
      script.onload = () => resolve();
      script.onerror = () => reject();
      document.body.appendChild(script);
    });
  }

  private detectarUbicacion(): void {
    navigator.geolocation?.getCurrentPosition(position => {
      this.form.latitud = Number(position.coords.latitude.toFixed(6));
      this.form.longitud = Number(position.coords.longitude.toFixed(6));
    }, () => undefined, { enableHighAccuracy: false, timeout: 5000 });
  }

  private cargarPlan(onSuccess?: () => void, onError?: () => void): void {
    this.http.get<PlanConfig>(`${this.apiUrl}/plan`).pipe(timeout(10000)).subscribe({
      next: plan => {
        this.plan = plan;
        this.planLoaded = true;
        this.error = '';
        this.cdr.detectChanges();
        onSuccess?.();
      },
      error: () => {
        this.planLoaded = false;
        if (!onError) {
          this.error = 'El plan está disponible, pero Spring Boot aún no responde. Puedes completar tus datos mientras se restablece la conexión.';
        }
        this.cdr.detectChanges();
        onError?.();
      }
    });
  }
}
