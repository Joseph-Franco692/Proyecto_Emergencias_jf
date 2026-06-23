import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { DetalleComponent } from './pages/detalle/detalle';
import { ReportarComponent } from './pages/reportar/reportar';
import { UnidadDashboardComponent } from './pages/unidad-dashboard/unidad-dashboard';
import { LoginComponent } from './pages/login/login';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'detalle/:id', component: DetalleComponent, canActivate: [authGuard] },
  { path: 'reportar', component: ReportarComponent },
  { path: 'unidad', component: UnidadDashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];