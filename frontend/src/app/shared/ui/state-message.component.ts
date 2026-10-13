import { Component, computed, input } from '@angular/core';
import { LucideAngularModule, Inbox, CircleAlert, Lock, RefreshCw } from 'lucide-angular';

export type StateKind = 'empty' | 'error' | 'forbidden' | 'loading';

const ICONS = { empty: Inbox, error: CircleAlert, forbidden: Lock, loading: RefreshCw };

/** Estado vazio, de erro, sem permissao ou carregando, com o mesmo visual em todas as paginas. */
@Component({
  selector: 'app-state-message',
  imports: [LucideAngularModule],
  template: `
    <div class="flex flex-col items-center justify-center px-6 py-14 text-center" [attr.role]="kind() === 'error' ? 'alert' : 'status'">
      <div class="mb-4 flex h-11 w-11 items-center justify-center rounded-xl" [class]="tone()">
        <lucide-icon [name]="icon()" class="h-5 w-5" [class.animate-spin]="kind() === 'loading'"></lucide-icon>
      </div>
      <p class="text-sm font-medium text-zinc-900">{{ title() }}</p>
      @if (message()) {
        <p class="mt-1 max-w-sm text-sm text-zinc-500">{{ message() }}</p>
      }
      <ng-content></ng-content>
    </div>
  `
})
export class StateMessageComponent {
  readonly kind = input<StateKind>('empty');
  readonly title = input.required<string>();
  readonly message = input<string | null>('');

  protected readonly icon = computed(() => ICONS[this.kind()]);
  protected readonly tone = computed(() => {
    switch (this.kind()) {
      case 'error': return 'bg-rose-50 text-rose-600';
      case 'forbidden': return 'bg-amber-50 text-amber-600';
      default: return 'bg-zinc-100 text-zinc-500';
    }
  });
}
