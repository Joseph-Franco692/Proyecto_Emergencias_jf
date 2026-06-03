import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterModule], 
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class AppComponent { 
  // ¡Mira qué limpio quedó! El padre ya no necesita hacer el trabajo pesado.
}