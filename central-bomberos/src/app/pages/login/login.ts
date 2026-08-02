import { Component, OnInit, NgZone, ChangeDetectorRef } from "@angular/core";
import { Router, ActivatedRoute } from "@angular/router";
import { AuthService } from "../../services/auth.service";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { switchMap, of } from "rxjs";

declare const google: any;
const GOOGLE_CLIENT_ID = "972842219867-4t1bv2l523jevau1uqjrforlfoj51hbg.apps.googleusercontent.com";

type Screen = 'google' | 'manual' | 'register' | 'verify' | 'setup2FA' | 'verify2FA'
  | 'linkZone' | 'forgotPassword' | 'resetPassword';

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
  confirmPassword = '';
  resetToken = '';
  name = '';
  code = '';
  qrUrl = '';
  qrKey = '';
  inputZoneCode = '';

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private zone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const resetToken = this.route.snapshot.queryParamMap.get('resetToken');
    if (resetToken) {
      this.resetToken = resetToken;
      this.setScreen('resetPassword');
      return;
    }

    // Verificar parámetros en URL (?verifyEmail=...&code=...)
    this.route.queryParams.subscribe(p => {
      if (p['verifyEmail'] && p['code']) {
        this.email = p['verifyEmail'];
        this.code = p['code'];
        this.setScreen('verify');
        this.doVerifyAccount();
      }
    });

    // Si ya hay sesión activa, ir al destino según su rol
    this.auth.checkSession().subscribe(ok => {
      if (ok) {
        this.redirectByRole();
      } else {
        this.initGoogleButton();
      }
    });
  }

  private redirectByRole(): void {
    const user = this.auth.getUser();
    if (user?.role === 'OPERADOR') {
      // Si el operador no ha vinculado su Código de Zona, mostrar pantalla de vinculación
      if (!user.zoneCode || user.requiresZoneLinking) {
        this.email = user.email || this.email;
        this.setScreen('linkZone');
        return;
      }
      this.router.navigate(["/unidad"]);
    } else {
      this.router.navigate(["/"]);
    }
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
          this.redirectByRole();
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
    if (!/^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]{3,80}$/.test(this.name.trim())) {
      this.error = "Ingresa un nombre completo válido, sin números ni caracteres especiales."; return;
    }
    if (!this.esCorreoValido(this.email)) {
      this.error = "Ingresa un correo electrónico válido."; return;
    }
    if (this.password.length < 8 || !/[A-Za-z]/.test(this.password) || !/\d/.test(this.password)) {
      this.error = "La contraseña debe tener al menos 8 caracteres, una letra y un número."; return;
    }
    this.name = this.name.trim().replace(/\s+/g, ' ');
    this.email = this.email.trim().toLowerCase();
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
    this.email = this.email.trim().toLowerCase();
    this.code = this.code.replace(/\D/g, '').slice(0, 6);
    if (!this.esCorreoValido(this.email)) { this.error = "Ingresa el correo utilizado al crear la cuenta."; return; }
    if (!/^\d{6}$/.test(this.code)) { this.error = "Ingresa un código numérico de 6 dígitos."; return; }
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
        this.cdr.detectChanges();
      }
    });
  }

  normalizeVerificationCode(): void {
    this.code = (this.code || '').replace(/\D/g, '').slice(0, 6);
    this.error = null;
  }

  // ─── RECUPERACIÓN DE CONTRASEÑA ───────────────────────────────────────────
  doRequestPasswordReset(): void {
    if (this.isLoading) return;
    if (!this.esCorreoValido(this.email)) {
      this.error = "Ingresa un correo electrónico válido."; return;
    }

    this.email = this.email.trim().toLowerCase();
    this.startLoading();
    this.auth.requestPasswordReset(this.email).subscribe({
      next: (r: any) => {
        this.stopLoading();
        this.success = r.message;
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  doResetPassword(): void {
    if (this.isLoading) return;
    if (this.password.length < 8 || this.password.length > 72
        || !/[A-Za-z]/.test(this.password) || !/\d/.test(this.password)) {
      this.error = "La contraseña debe tener entre 8 y 72 caracteres, una letra y un número."; return;
    }
    if (this.password !== this.confirmPassword) {
      this.error = "Las contraseñas no coinciden."; return;
    }

    this.startLoading();
    this.auth.resetPassword(this.resetToken, this.password).subscribe({
      next: (r: any) => {
        this.stopLoading();
        this.password = '';
        this.confirmPassword = '';
        this.resetToken = '';
        this.success = r.message;
        this.router.navigate(['/login'], { replaceUrl: true });
        this.setScreen('manual');
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  // ─── VINCULAR CÓDIGO DE ZONA ─────────────────────────────────────────────────
  doLinkZone(): void {
    if (this.isLoading) return;
    if (!/^ZONA-[A-Z0-9-]{3,30}$/.test(this.inputZoneCode.trim().toUpperCase())) {
      this.error = "Ingresa el Código de Zona brindado por tu Administrador."; return;
    }
    this.inputZoneCode = this.inputZoneCode.trim().toUpperCase();
    this.startLoading();

    this.auth.linkZone(this.inputZoneCode.trim(), this.email).subscribe({
      next: (r: any) => {
        this.stopLoading();
        this.success = r.message || "¡Zona vinculada exitosamente!";
        this.redirectByRole();
      },
      error: (e: Error) => {
        this.stopLoading();
        this.error = e.message;
      }
    });
  }

  // ─── LOGIN MANUAL ─────────────────────────────────────────────────────────────
  doLogin(): void {
    if (this.isLoading) return;
    if (!this.email.trim() || !this.password.trim()) {
      this.error = "Ingresa tu correo y contraseña."; return;
    }
    if (!this.esCorreoValido(this.email)) {
      this.error = "Ingresa un correo electrónico válido."; return;
    }
    this.email = this.email.trim().toLowerCase();
    this.startLoading();

    this.auth.loginManualFirstStep(this.email.trim(), this.password).pipe(
      switchMap((r: any) => {
        if (r.requiresMfa) {
          return of({ ...r, _action: 'verify2FA' });
        }
        if (r.requiresMfaSetup) {
          return this.auth.setupMfa(this.email.trim()).pipe(
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
            this.redirectByRole();
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
    if (!/^\d{6}$/.test(this.code)) { this.error = "El código debe tener 6 dígitos numéricos."; return; }
    this.startLoading();

    this.auth.confirmMfaSetup(this.email, this.code).subscribe({
      next: () => {
        this.stopLoading();
        this.redirectByRole();
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
    if (!/^\d{6}$/.test(this.code)) { this.error = "El código debe tener 6 dígitos numéricos."; return; }
    this.startLoading();

    this.auth.verifyMfa(this.email, this.code).subscribe({
      next: () => {
        this.stopLoading();
        this.redirectByRole();
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

  private esCorreoValido(correo: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[A-Za-z]{2,}$/.test(correo.trim());
  }

  isManualTab(): boolean {
    return ['manual', 'register', 'verify', 'setup2FA', 'verify2FA', 'linkZone',
      'forgotPassword', 'resetPassword'].includes(this.screen);
  }
}
