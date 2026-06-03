import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { DetalleComponent } from './pages/detalle/detalle';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'detalle/:id', component: DetalleComponent },
  { path: '**', redirectTo: '' }
];