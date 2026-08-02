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
  const isCitizenReportCreation =
    req.method === 'POST' &&
    (req.url.endsWith('/api/reportes') || req.url.endsWith('/api/reportes/'));
  const isPublicRequest =
    req.url.includes('/api/premium/') ||
    req.url.endsWith('/api/premium') ||
    isCitizenReportCreation;

  const handleAuthError = (error: HttpErrorResponse) => {
    // Un 403 significa que la sesión es válida pero el usuario no tiene permiso.
    // Solo un 401 debe invalidar la sesión local.
    if (error.status === 401) {
      authService.logout().subscribe();
      router.navigate(['/login']);
    }
    return throwError(() => error);
  };

  const isBackendRequest = req.url.startsWith('/api/');
  if (token && isBackendRequest && !isPublicRequest) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq).pipe(catchError(handleAuthError));
  }

  return next(req).pipe(catchError(handleAuthError));
};
