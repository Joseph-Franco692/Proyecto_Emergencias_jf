import { Component, OnInit, NgZone, ChangeDetectorRef, ChangeDetectionStrategy } from "@angular/core";
import { Router, ActivatedRoute } from "@angular/router";
import { AuthService } from "../../services/auth.service";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { switchMap, of } from "rxjs";

declare const google: any;
const GOOGLE_CLIENT_ID = "972842219867-4t1bv2l523jevau1uqjrforlfoj51hbg.apps.googleusercontent.com";

type Screen = 'google' | 'manual' | 'register' | 'verify' | 'setup2FA' | 'verify2FA';

@Component({
  selector: "app-login",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./login.html",
  styleUrls: ["./login.css"]
})
export class LoginComponent implements OnInit {
  screen: Screen = 'google';
  isLoading = false;
  error: string | null = null;
  success: string | null = null;

  email = '';
  password = '';
  name = '';
  code = '';
  qrUrl = '';
  qrKey = '';

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private zone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Verificar parámetros en URL (?verifyEmail=...&code=...)
    this.route.queryParams.subscribe(p => {
      if (p['verifyEmail'] && p['code']) {
        this.email = p['verifyEmail'];
        this.code = p['code'];
        this.setScreen('verify');
        this.doVerifyAccount();
      }
    });

    // Si ya hay sesión activa, ir al dashboard
    this.auth.checkSession().subscribe(ok => {
      if (ok) {
        this.router.navigate(["/"]);
      } else {
        this.initGoogleButton();
      }
    });
  }

  // ─── GOOGLE ──────────────────────────────────────────────────────────────────
  private initGoogleButton(): void {
    const tryInit = () => {
      if (typeof google !== "undefined" && google?.accounts?.id) {
        const container = document.getElementById("google-btn-container");
        if (container) {
          container.innerHTML = "";
          google.accounts.id.initialize({
            client_id: GOOGLE_CLIENT_ID,
            callback: (r: any) => this.onGoogleCallback(r)
          });
          google.accounts.id.renderButton(container, {
            theme: "outline", size: "large", shape: "rectangular", width: 320
          });
        }
      } else {
        setTimeout(tryInit, 300);
      }
    };
    tryInit();
  }

  private onGoogleCallback(response: any): void {
    this.zone.run(() => {
      this.startLoading();
      this.auth.loginWithGoogle(response.credential).subscribe({
        next: () => {
          this.stopLoading();
          this.router.navigate(["/"]);
        },
        error: (e: Error) => {
          this.stopLoading();
          this.error = e.message;
        }
      });
    });
  }

  // ─── NAVEGACIÓN ──────────────────────────────────────────────────────────────
  goTo(s: Screen): void {
    this.error = null;
    this.success = null;
    this.isLoading = false;
    this.setScreen(s);
    if (s === 'google') setTimeout(() => this.initGoogleButton(), 100);
  }

  // Actualiza la pantalla y fuerza detección de cambios
  private setScreen(s: Screen): void {
    this.screen = s;
    this.cdr.detectChanges();
  }

  // ─── REGISTRO ────────────────────────────────────────────────────────────────
  doRegister(): void {
    if (this.isLoading) return;
    if (!this.name.trim() || !this.email.trim() || !this.password.trim()) {
      this.error = "Todos los campos son obligatorios."; return;
    }
    this.startLoading();

    this.auth.registerManual({ name: this.name.trim(), email: this.email.trim(), password: this.password }).subscribe({
      next: (r: any) => {
        this.stopLoading();
        this.success = r.message || "Código enviado a tu correo.";
        this.setScreen('verify');
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  doResendCode(): void {
    if (this.isLoading || !this.email) return;
    this.startLoading();
    this.auth.resendCode(this.email).subscribe({
      next: (r: any) => { this.stopLoading(); this.success = r.message; },
      error: (e: Error) => { this.stopLoading(); this.error = e.message; }
    });
  }

  doVerifyAccount(): void {
    if (this.isLoading) return;
    if (!this.code.trim()) { this.error = "Ingresa el código de 6 dígitos."; return; }
    this.startLoading();

    this.auth.verifyAccount(this.email, this.code).subscribe({
      next: (r: any) => {
        this.stopLoading();
        this.code = '';
        this.password = '';
        this.success = r.message || "¡Cuenta activada! Inicia sesión.";
        this.setScreen('manual');
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  // ─── LOGIN MANUAL (encadenado con switchMap, no callbacks anidados) ───────────
  doLogin(): void {
    if (this.isLoading) return;
    if (!this.email.trim() || !this.password.trim()) {
      this.error = "Ingresa tu correo y contraseña."; return;
    }
    this.startLoading();

    // Encadenar loginManualFirstStep → si necesita setup, llama a setupMfa en el mismo stream
    this.auth.loginManualFirstStep(this.email.trim(), this.password).pipe(
      switchMap((r: any) => {
        if (r.requiresMfa) {
          // Ya tiene 2FA → simplemente pasar el resultado para mostrar la pantalla
          return of({ ...r, _action: 'verify2FA' });
        }
        if (r.requiresMfaSetup) {
          // Primera vez → llamar setup en el mismo observable
          return this.auth.setupMfa(this.email.trim()).pipe(
            // Marcar el resultado como 'setup'
            switchMap((setup: any) => of({ ...setup, _action: 'setup2FA' }))
          );
        }
        if (r.token) {
          return of({ ...r, _action: 'dashboard' });
        }
        return of({ ...r, _action: 'unknown' });
      })
    ).subscribe({
      next: (result: any) => {
        this.stopLoading();

        switch (result._action) {
          case 'verify2FA':
            this.setScreen('verify2FA');
            break;

          case 'setup2FA':
            this.qrUrl  = result.qrCode    || '';
            this.qrKey  = result.manualKey || '';
            this.code   = '';
            this.setScreen('setup2FA');
            break;

          case 'dashboard':
            this.router.navigate(["/"]);
            break;

          default:
            this.error = "Respuesta inesperada del servidor.";
            break;
        }
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  // ─── CONFIRMAR 2FA SETUP ──────────────────────────────────────────────────────
  doConfirmMFA(): void {
    if (this.isLoading) return;
    if (this.code.length < 6) { this.error = "El código debe tener 6 dígitos."; return; }
    this.startLoading();

    this.auth.confirmMfaSetup(this.email, this.code).subscribe({
      next: () => {
        this.stopLoading();
        this.router.navigate(["/"]);
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  // ─── VERIFICAR 2FA (LOGIN EXISTENTE) ──────────────────────────────────────────
  doVerify2FA(): void {
    if (this.isLoading) return;
    if (this.code.length < 6) { this.error = "El código debe tener 6 dígitos."; return; }
    this.startLoading();

    this.auth.verifyMfa(this.email, this.code).subscribe({
      next: () => {
        this.stopLoading();
        this.router.navigate(["/"]);
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  // ─── HELPERS ──────────────────────────────────────────────────────────────────
  private startLoading(): void {
    this.isLoading = true;
    this.error     = null;
    this.success   = null;
    this.cdr.detectChanges();
  }

  private stopLoading(): void {
    this.isLoading = false;
    this.cdr.detectChanges();
  }

  isManualTab(): boolean {
    return ['manual', 'register', 'verify', 'setup2FA', 'verify2FA'].includes(this.screen);
  }
}
