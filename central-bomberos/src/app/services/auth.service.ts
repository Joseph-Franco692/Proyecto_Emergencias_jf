import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable, BehaviorSubject, of } from "rxjs";
import { map, catchError, tap } from "rxjs/operators";

export interface GoogleUser {
  id: string;
  email: string;
  name: string;
  picture: string;
}

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly AUTH_SERVER = "http://localhost:3000";
  private currentUser$ = new BehaviorSubject<GoogleUser | null>(null);

  constructor(private http: HttpClient) {}

  /** Verifica con el servidor Node si la sesion sigue activa */
  checkSession(): Observable<boolean> {
    return this.http
      .get<{ success: boolean; user: GoogleUser }>(`${this.AUTH_SERVER}/api/session`, {
        withCredentials: true
      })
      .pipe(
        tap(resp => this.currentUser$.next(resp.user)),
        map(() => true),
        catchError(() => {
          this.currentUser$.next(null);
          return of(false);
        })
      );
  }

  /** Envia el token de Google al servidor para validarlo y crear sesion */
  loginWithGoogle(credential: string): Observable<GoogleUser> {
    return this.http
      .post<{ success: boolean; user: GoogleUser, token: string }>(
        `${this.AUTH_SERVER}/api/auth/google`,
        { credential },
        { withCredentials: true }
      )
      .pipe(
        tap(resp => {
          this.currentUser$.next(resp.user);
          if (resp.token) {
            localStorage.setItem('jwt_token', resp.token);
          }
        }),
        map(resp => resp.user)
      );
  }

  /** Cierra la sesion en el servidor Node */
  logout(): Observable<any> {
    return this.http
      .post(`${this.AUTH_SERVER}/api/auth/logout`, {}, { withCredentials: true })
      .pipe(tap(() => {
        this.currentUser$.next(null);
        localStorage.removeItem('jwt_token');
      }));
  }

  getToken(): string | null {
    return localStorage.getItem('jwt_token');
  }

  getUser(): GoogleUser | null {
    return this.currentUser$.getValue();
  }

  getUser$() {
    return this.currentUser$.asObservable();
  }
}
