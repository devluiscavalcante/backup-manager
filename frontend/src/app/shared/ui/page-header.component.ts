import { Component, input } from '@angular/core';

/** Cabecalho padrao das paginas: titulo, descricao curta e acoes a direita. */
@Component({
  selector: 'app-page-header',
  template: `
    <header class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div class="min-w-0">
        <h1 class="text-2xl font-semibold tracking-tight text-zinc-900">{{ title() }}</h1>
        @if (description()) {
          <p class="mt-1 text-sm text-zinc-500">{{ description() }}</p>
        }
      </div>
      <div class="flex shrink-0 flex-wrap items-center gap-2">
        <ng-content></ng-content>
      </div>
    </header>
  `
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
