import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterModule], 
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class AppComponent {
  constructor(public themeService: ThemeService) {}
}
