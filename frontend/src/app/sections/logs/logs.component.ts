import { Component, OnInit, computed, signal, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, Search, Download, AlertCircle, Info, XCircle, FileText, RefreshCw, Terminal } from 'lucide-angular';
import { LogsService, LogEntry, LogLevel } from '../../core/services/logs.service';
import { apiErrorMessage } from '../../core/api/api.models';

type LevelFilter = 'all' | LogLevel;

const POLLING_INTERVAL_MS = 10_000;

@Component({
  selector: 'app-logs',
  imports: [LucideAngularModule, FormsModule],
  templateUrl: './logs.component.html'
})
export class LogsComponent implements OnInit, OnDestroy {
  private logsService = inject(LogsService);

  readonly terminalIcon = Terminal; readonly searchIcon = Search; readonly refreshIcon = RefreshCw;
  readonly downloadIcon = Download; readonly infoIcon = Info;
  readonly errorIcon = XCircle; readonly warningIcon = AlertCircle; readonly fileIcon = FileText;

  readonly filters: LevelFilter[] = ['all', 'warning', 'error', 'info'];

  logs = signal<LogEntry[]>([]);
  rawContent = signal('');
  filter = signal<LevelFilter>('all');
  search = signal('');
  isLoading = signal(true);
  /** Nenhum warnings.log gerado ainda (backend responde 404). */
  isEmpty = signal(false);
  error = signal<string | null>(null);
  private intervalId?: ReturnType<typeof setInterval>;

  filteredLogs = computed(() => {
    const currentFilter = this.filter();
    const searchTerm = this.search().toLowerCase();

    return this.logs().filter(log =>
      (currentFilter === 'all' || log.level === currentFilter) &&
      log.message.toLowerCase().includes(searchTerm)
    );
  });

  ngOnInit() {
    this.loadLogs();
    this.intervalId = setInterval(() => this.loadLogs(), POLLING_INTERVAL_MS);
  }

  ngOnDestroy() {
    clearInterval(this.intervalId);
  }

  loadLogs() {
    this.isLoading.set(true);
    this.logsService.getWarningsLog().subscribe({
      next: log => {
        this.logs.set(log.entries);
        this.rawContent.set(log.raw);
        this.isEmpty.set(false);
        this.error.set(null);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        this.logs.set([]);
        this.rawContent.set('');
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.isEmpty.set(true);
          this.error.set(null);
        } else if (err instanceof HttpErrorResponse && err.status === 403) {
          this.error.set('Logs are only available to administrator accounts.');
          clearInterval(this.intervalId);
        } else {
          this.error.set(apiErrorMessage(err, 'Failed to load logs.'));
        }
        this.isLoading.set(false);
      }
    });
  }

  exportLogs() {
    const content = this.rawContent();
    if (!content) return;

    const url = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'warnings.log';
    link.click();
    URL.revokeObjectURL(url);
  }

  getLevelColor(level: LogLevel): string {
    switch (level) {
      case 'warning': return 'bg-yellow-100 text-yellow-800 border-yellow-200';
      case 'error': return 'bg-red-100 text-red-800 border-red-200';
      default: return 'bg-blue-100 text-blue-800 border-blue-200';
    }
  }
}
