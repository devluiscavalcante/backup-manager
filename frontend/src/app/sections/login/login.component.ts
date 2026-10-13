import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, ShieldCheck, LogIn } from 'lucide-angular';
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

  readonly shieldIcon = ShieldCheck;
  readonly loginIcon = LogIn;

  username = '';
  password = '';
  isSubmitting = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    if (this.isSubmitting()) {
      return;
    }
    if (!this.username.trim() || !this.password) {
      this.error.set('Informe usuário e senha.');
      return;
    }

    this.isSubmitting.set(true);
    this.error.set(null);

    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        this.password = '';
        this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')));
      },
      error: (err: unknown) => {
        this.isSubmitting.set(false);
        this.password = '';
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 401
            ? 'Usuário ou senha inválidos.'
            : apiErrorMessage(err, 'Não foi possível entrar.')
        );
      }
    });
  }
}

/** Aceita apenas caminhos internos da aplicacao, evitando redirecionamento para outro site. */
function safeReturnUrl(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\') || value.startsWith('/login')) {
    return '/inicio';
  }
  return value;
}
