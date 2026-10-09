import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, Shield, LogIn } from 'lucide-angular';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../core/api/api.models';

@Component({
  selector: 'app-login',
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  readonly shieldIcon = Shield;
  readonly loginIcon = LogIn;

  username = '';
  password = '';
  isSubmitting = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    if (!this.username.trim() || !this.password || this.isSubmitting()) {
      return;
    }

    this.isSubmitting.set(true);
    this.error.set(null);

    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/inicio';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err: unknown) => {
        this.isSubmitting.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 401
            ? 'Usuario ou senha invalidos.'
            : apiErrorMessage(err, 'Nao foi possivel entrar.')
        );
      }
    });
  }
}
