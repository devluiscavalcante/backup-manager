import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { LucideAngularModule, History, ArrowRight } from 'lucide-angular';
import { BackupService, BackupRecord, BackupStatus } from '../../core/services/backup.service';
import { apiErrorMessage } from '../../core/api/api.models';

const STATUS_STYLES: Record<BackupStatus, { label: string; css: string }> = {
  EM_ANDAMENTO: { label: 'In progress', css: 'bg-blue-50 text-blue-600 border-blue-100/50' },
  PAUSADO: { label: 'Paused', css: 'bg-amber-50 text-amber-600 border-amber-100/50' },
  CONCLUIDO: { label: 'Completed', css: 'bg-emerald-50 text-emerald-600 border-emerald-100/50' },
  FALHA: { label: 'Failed', css: 'bg-red-50 text-red-600 border-red-100/50' },
  CANCELADO: { label: 'Cancelled', css: 'bg-gray-100 text-gray-500 border-gray-200/50' }
};

@Component({
  selector: 'app-history',
  imports: [DatePipe, DecimalPipe, LucideAngularModule],
  templateUrl: './history.component.html'
})
export class HistoryComponent implements OnInit {
  private backupService = inject(BackupService);

  readonly historyIcon = History;
  readonly arrowIcon = ArrowRight;

  historyRecords = signal<BackupRecord[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);

  ngOnInit() {
    this.loadHistory();
  }

  loadHistory() {
    this.isLoading.set(true);
    this.error.set(null);
    this.backupService.getHistory().subscribe({
      next: records => {
        // Mais recentes primeiro.
        this.historyRecords.set(
          [...records].sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''))
        );
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err, 'Nao foi possivel carregar o historico.'));
        this.isLoading.set(false);
      }
    });
  }

  statusLabel(status: BackupStatus): string {
    return STATUS_STYLES[status]?.label ?? status;
  }

  statusClass(status: BackupStatus): string {
    return STATUS_STYLES[status]?.css ?? 'bg-gray-100 text-gray-500 border-gray-200/50';
  }
}
