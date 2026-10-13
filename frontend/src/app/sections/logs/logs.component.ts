import { Component, OnInit, computed, signal, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, Search, Download, RefreshCw, TriangleAlert, CircleX, Info } from 'lucide-angular';
import { LogsService, LogEntry, LogLevel } from '../../core/services/logs.service';
import { apiErrorMessage } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StateMessageComponent } from '../../shared/ui/state-message.component';
import { maskPathsInText } from '../../shared/format/path';

type LevelFilter = 'all' | LogLevel;

const POLLING_INTERVAL_MS = 10_000;

const LEVEL_LABELS: Record<LogLevel, string> = { warning: 'Aviso', error: 'Erro', info: 'Info' };

@Component({
  selector: 'app-logs',
  imports: [LucideAngularModule, FormsModule, PageHeaderComponent, StateMessageComponent],
  templateUrl: './logs.component.html'
})
export class LogsComponent implements OnInit, OnDestroy {
  private logsService = inject(LogsService);

  readonly searchIcon = Search;
  readonly refreshIcon = RefreshCw;
  readonly downloadIcon = Download;
  readonly warningIcon = TriangleAlert;
  readonly errorIcon = CircleX;
  readonly infoIcon = Info;

  readonly filters: { value: LevelFilter; label: string }[] = [
    { value: 'all', label: 'Todos' },
    { value: 'warning', label: 'Avisos' },
    { value: 'error', label: 'Erros' },
    { value: 'info', label: 'Info' }
  ];

  /** Entradas ja com os caminhos mascarados: nada do arquivo original chega ao DOM. */
  logs = signal<LogEntry[]>([]);
  filter = signal<LevelFilter>('all');
  search = signal('');
  isLoading = signal(true);
  /** Nenhum warnings.log gerado ainda (backend responde 404). */
  isEmpty = signal(false);
  forbidden = signal(false);
  error = signal<string | null>(null);
  private intervalId?: ReturnType<typeof setInterval>;

  filteredLogs = computed(() => {
    const currentFilter = this.filter();
    const searchTerm = this.search().trim().toLowerCase();

    return this.logs().filter(log =>
      (currentFilter === 'all' || log.level === currentFilter) &&
      log.message.toLowerCase().includes(searchTerm)
    );
  });

  ngOnInit() {
    this.loadLogs();
    // Nao consulta o servidor enquanto a aba estiver em segundo plano.
    this.intervalId = setInterval(() => {
      if (!document.hidden) this.loadLogs();
    }, POLLING_INTERVAL_MS);
  }

  ngOnDestroy() {
    clearInterval(this.intervalId);
  }

  loadLogs() {
    this.isLoading.set(true);
    this.logsService.getWarningsLog().subscribe({
      next: log => {
        this.logs.set(log.entries.map(e => ({ ...e, message: maskPathsInText(e.message) })));
        this.isEmpty.set(false);
        this.error.set(null);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        this.logs.set([]);
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.isEmpty.set(true);
          this.error.set(null);
        } else if (err instanceof HttpErrorResponse && err.status === 403) {
          this.forbidden.set(true);
          this.error.set('Os alertas são exclusivos para administradores.');
          clearInterval(this.intervalId);
        } else {
          this.error.set(apiErrorMessage(err, 'Não foi possível carregar os alertas.'));
        }
        this.isLoading.set(false);
      }
    });
  }

  /** Exporta a versao exibida (filtrada e com caminhos mascarados). */
  exportLogs() {
    const lines = this.filteredLogs().map(l =>
      `${l.timestamp ? `[${l.timestamp}] ` : ''}[${l.level.toUpperCase()}] ${l.message}`
    );
    if (lines.length === 0) return;

    const url = URL.createObjectURL(new Blob([lines.join('\n') + '\n'], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'alertas-backup.log';
    link.click();
    URL.revokeObjectURL(url);
  }

  levelLabel(level: LogLevel): string {
    return LEVEL_LABELS[level];
  }

  levelClass(level: LogLevel): string {
    switch (level) {
      case 'warning': return 'bg-amber-50 text-amber-600';
      case 'error': return 'bg-rose-50 text-rose-600';
      default: return 'bg-sky-50 text-sky-600';
    }
  }
}
