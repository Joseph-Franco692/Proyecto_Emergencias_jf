import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { map } from "rxjs/operators";
import { AuthService } from "../services/auth.service";

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.checkSession().pipe(
    map(isLoggedIn => {
      if (!isLoggedIn) {
        router.navigate(["/login"]);
        return false;
      }

      const user = authService.getUser();
      const isOperador = user?.role === 'OPERADOR';
      const targetUrl = state.url;

      if (isOperador) {
        // Los operadores únicamente tienen acceso al módulo de unidad (/unidad)
        if (!targetUrl.startsWith('/unidad')) {
          console.warn(`[AUTH GUARD] Operador ${user?.email} intentó acceder a ${targetUrl}. Redirigiendo a /unidad...`);
          router.navigate(['/unidad']);
          return false;
        }
      }

      return true;
    })
  );
};
