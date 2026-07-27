import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getToken();
  const isPublicRequest =
    req.url.includes('/api/premium/') ||
    req.url.endsWith('/api/premium') ||
    req.url.includes('/api/reportes');

  const handleAuthError = (error: HttpErrorResponse) => {
    // Un 403 significa que la sesión es válida pero el usuario no tiene permiso.
    // Solo un 401 debe invalidar la sesión local.
    if (error.status === 401) {
      authService.logout().subscribe();
      router.navigate(['/login']);
    }
    return throwError(() => error);
  };

  if (token && req.url.includes('localhost:8081') && !isPublicRequest) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq).pipe(catchError(handleAuthError));
  }

  return next(req).pipe(catchError(handleAuthError));
};
