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

  const handleAuthError = (error: HttpErrorResponse) => {
    if (error.status === 401 || error.status === 403) {
      authService.logout().subscribe();
      router.navigate(['/login']);
    }
    return throwError(() => error);
  };

  if (token && req.url.includes('localhost:8081')) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq).pipe(catchError(handleAuthError));
  }

  return next(req).pipe(catchError(handleAuthError));
};
