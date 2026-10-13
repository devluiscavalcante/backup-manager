import { Component, computed, input } from '@angular/core';
import { BackupStatus } from '../../core/services/backup.service';

export const STATUS_LABELS: Record<BackupStatus, string> = {
  EM_ANDAMENTO: 'Em andamento',
  PAUSADO: 'Pausado',
  CONCLUIDO: 'Concluído',
  FALHA: 'Falhou',
  CANCELADO: 'Cancelado'
};

const STATUS_STYLES: Record<BackupStatus, { badge: string; dot: string }> = {
  EM_ANDAMENTO: { badge: 'bg-indigo-50 text-indigo-700 ring-indigo-600/15', dot: 'bg-indigo-500' },
  PAUSADO: { badge: 'bg-amber-50 text-amber-700 ring-amber-600/20', dot: 'bg-amber-500' },
  CONCLUIDO: { badge: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', dot: 'bg-emerald-500' },
  FALHA: { badge: 'bg-rose-50 text-rose-700 ring-rose-600/20', dot: 'bg-rose-500' },
  CANCELADO: { badge: 'bg-zinc-100 text-zinc-600 ring-zinc-500/20', dot: 'bg-zinc-400' }
};

const FALLBACK = { badge: 'bg-zinc-100 text-zinc-600 ring-zinc-500/20', dot: 'bg-zinc-400' };

@Component({
  selector: 'app-status-badge',
  template: `
    <span class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset"
          [class]="style().badge">
      <span class="h-1.5 w-1.5 rounded-full" [class]="style().dot"></span>
      {{ label() }}
    </span>
  `
})
export class StatusBadgeComponent {
  readonly status = input.required<BackupStatus>();

  protected readonly style = computed(() => STATUS_STYLES[this.status()] ?? FALLBACK);
  protected readonly label = computed(() => STATUS_LABELS[this.status()] ?? this.status());
}
