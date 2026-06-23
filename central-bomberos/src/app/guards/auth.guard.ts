import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { map } from "rxjs/operators";
import { AuthService } from "../services/auth.service";

export const authGuard: CanActivateFn = (_route, _state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.checkSession().pipe(
    map(isLoggedIn => {
      if (isLoggedIn) return true;
      router.navigate(["/login"]);
      return false;
    })
  );
};
