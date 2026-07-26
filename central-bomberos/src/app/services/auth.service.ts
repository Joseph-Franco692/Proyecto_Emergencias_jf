import { Injectable } from "@angular/core";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Observable, BehaviorSubject, of, throwError } from "rxjs";
import { map, catchError, tap } from "rxjs/operators";

export interface AppUser {
  id: string;
  email: string;
  name: string;
  picture: string;
  role?: string; // 'ADMIN' | 'OPERADOR'
  zoneCode?: string;
  requiresZoneLinking?: boolean;
  mfaEnabled?: boolean;
  status?: string;
}

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly API = "http://localhost:8081";
  private currentUser$ = new BehaviorSubject<AppUser | null>(null);

  constructor(private http: HttpClient) {}

  // Verifica si hay sesión activa usando el token guardado en localStorage
  checkSession(): Observable<boolean> {
    const token = this.getToken();
    if (!token) {
      this.clear();
      return of(false);
    }
    // Restaurar usuario en memoria si existe
    const saved = localStorage.getItem("jwt_user");
    if (saved) {
      try { this.currentUser$.next(JSON.parse(saved)); } catch {}
    }
    // Validar token con el backend
    return this.http.get<{ success: boolean; user: AppUser }>(
      `${this.API}/api/auth/session`,
      { headers: { Authorization: `Bearer ${token}` } }
    ).pipe(
      tap(r => { if (r?.user) this.saveSession(r.user, token); }),
      map(() => true),
      catchError(() => { this.clear(); return of(false); })
    );
  }

  // ─── GOOGLE OAUTH ────────────────────────────────────────────────────────────
  loginWithGoogle(credential: string): Observable<AppUser> {
    return this.http.post<{ success: boolean; user: AppUser; token: string }>(
      `${this.API}/api/auth/google`,
      { credential }
    ).pipe(
      tap(r => { if (r?.token && r?.user) this.saveSession(r.user, r.token); }),
      map(r => r.user),
      catchError(this.handleError)
    );
  }

  // ─── LOGOUT ───────────────────────────────────────────────────────────────────
  logout(): Observable<any> {
    return this.http.post(`${this.API}/api/auth/logout`, {}).pipe(
      tap(() => this.clear()),
      catchError(() => { this.clear(); return of(null); })
    );
  }

  // ─── REGISTRO MANUAL ─────────────────────────────────────────────────────────
  registerManual(data: { name: string; email: string; password: string }): Observable<any> {
    return this.http.post(`${this.API}/api/auth/register`, data).pipe(catchError(this.handleError));
  }

  resendCode(email: string): Observable<any> {
    return this.http.post(`${this.API}/api/auth/resend-code`, { email }).pipe(catchError(this.handleError));
  }

  verifyAccount(email: string, code: string): Observable<any> {
    return this.http.post(`${this.API}/api/auth/verify`, { email, code }).pipe(catchError(this.handleError));
  }

  // ─── VINCULACIÓN A CÓDIGO DE ZONA ─────────────────────────────────────────────
  linkZone(zoneCode: string, email?: string): Observable<any> {
    const token = this.getToken();
    const userEmail = email || this.getUser()?.email;
    const headers: { [header: string]: string } = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    return this.http.post<any>(`${this.API}/api/auth/link-zone`, { zoneCode, email: userEmail }, { headers }).pipe(
      tap((r: any) => {
        if (r?.user) {
          const currentToken = this.getToken();
          if (currentToken) {
            this.saveSession(r.user, currentToken);
          }
        }
      }),
      catchError(this.handleError)
    );
  }

  // ─── LOGIN MANUAL + 2FA ───────────────────────────────────────────────────────
  loginManualFirstStep(email: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.API}/api/mfa/login`, { email, password }).pipe(
      tap(r => {
        if (r?.token && r?.user) this.saveSession(r.user, r.token);
      }),
      catchError(this.handleError)
    );
  }

  setupMfa(email: string): Observable<any> {
    return this.http.post(`${this.API}/api/mfa/setup`, { email }).pipe(catchError(this.handleError));
  }

  confirmMfaSetup(email: string, code: string): Observable<any> {
    return this.http.post<any>(`${this.API}/api/mfa/confirm-setup`, { email, code }).pipe(
      tap(r => { if (r?.token && r?.user) this.saveSession(r.user, r.token); }),
      catchError(this.handleError)
    );
  }

  verifyMfa(email: string, code: string): Observable<any> {
    return this.http.post<any>(`${this.API}/api/mfa/verify`, { email, code }).pipe(
      tap(r => { if (r?.token && r?.user) this.saveSession(r.user, r.token); }),
      catchError(this.handleError)
    );
  }

  // ─── GESTIÓN DE ROLES (ADMIN) ────────────────────────────────────────────────
  getUsers(): Observable<AppUser[]> {
    const token = this.getToken();
    return this.http.get<AppUser[]>(`${this.API}/api/admin/users`, {
      headers: { Authorization: `Bearer ${token}` }
    }).pipe(catchError(this.handleError));
  }

  updateUserRole(userId: string, role: string): Observable<any> {
    const token = this.getToken();
    return this.http.put(`${this.API}/api/admin/users/${userId}/role`, { role }, {
      headers: { Authorization: `Bearer ${token}` }
    }).pipe(catchError(this.handleError));
  }

  isAdmin(): boolean {
    const u = this.getUser();
    return u?.role === 'ADMIN';
  }

  isOperador(): boolean {
    const u = this.getUser();
    return u?.role === 'OPERADOR';
  }

  // ─── HELPERS ─────────────────────────────────────────────────────────────────
  private saveSession(user: AppUser, token: string): void {
    this.currentUser$.next(user);
    localStorage.setItem("jwt_token", token);
    localStorage.setItem("jwt_user", JSON.stringify(user));
  }

  private clear(): void {
    this.currentUser$.next(null);
    localStorage.removeItem("jwt_token");
    localStorage.removeItem("jwt_user");
  }

  private handleError(err: HttpErrorResponse): Observable<never> {
    let message = "Error de conexión con el servidor.";
    if (err.error) {
      if (typeof err.error === "string") {
        try {
          const parsed = JSON.parse(err.error);
          message = parsed.message || message;
        } catch { message = err.error; }
      } else if (err.error?.message) {
        message = err.error.message;
      }
    } else if (err.message) {
      message = err.message;
    }
    return throwError(() => new Error(message));
  }

  getToken(): string | null { return localStorage.getItem("jwt_token"); }
  getUser(): AppUser | null { return this.currentUser$.getValue(); }
  getUser$() { return this.currentUser$.asObservable(); }
}
