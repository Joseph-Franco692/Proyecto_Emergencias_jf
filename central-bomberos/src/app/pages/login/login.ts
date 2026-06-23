import { Component, OnInit, NgZone } from "@angular/core";
import { Router } from "@angular/router";
import { AuthService } from "../../services/auth.service";
import { CommonModule } from "@angular/common";

declare const google: any;

const GOOGLE_CLIENT_ID =
  "972842219867-4t1bv2l523jevau1uqjrforlfoj51hbg.apps.googleusercontent.com";

@Component({
  selector: "app-login",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./login.html",
  styleUrls: ["./login.css"]
})
export class LoginComponent implements OnInit {
  public isLoading = false;
  public error: string | null = null;

  constructor(
    private authService: AuthService,
    private router: Router,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    // Si ya hay sesion activa, redirigir directo
    this.authService.checkSession().subscribe(loggedIn => {
      if (loggedIn) this.router.navigate(["/"]);
      else this.initGoogleButton();
    });
  }

  private initGoogleButton(): void {
    const tryInit = () => {
      if (typeof google !== "undefined" && google.accounts) {
        google.accounts.id.initialize({
          client_id: GOOGLE_CLIENT_ID,
          callback: (resp: any) => this.handleGoogleResponse(resp)
        });
        google.accounts.id.renderButton(
          document.getElementById("google-btn-container"),
          { theme: "outline", size: "large", shape: "rectangular", width: 320 }
        );
      } else {
        setTimeout(tryInit, 300);
      }
    };
    tryInit();
  }

  private handleGoogleResponse(response: any): void {
    this.ngZone.run(() => {
      this.isLoading = true;
      this.error = null;
    });

    this.authService.loginWithGoogle(response.credential).subscribe({
      next: () => {
        this.ngZone.run(() => {
          this.isLoading = false;
          this.router.navigate(["/"]);
        });
      },
      error: (err) => {
        this.ngZone.run(() => {
          this.isLoading = false;
          this.error = "No se pudo autenticar. Intenta nuevamente.";
          console.error("Auth error:", err);
        });
      }
    });
  }
}
