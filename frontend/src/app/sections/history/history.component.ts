import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, RefreshCw, Search, ArrowRight } from 'lucide-angular';
import { BackupService, BackupRecord, BackupStatus } from '../../core/services/backup.service';
import { apiErrorMessage } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { STATUS_LABELS, StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { StateMessageComponent } from '../../shared/ui/state-message.component';
import { MaskPathPipe, MaskPathsPipe } from '../../shared/format/mask-path.pipe';
import { maskPath } from '../../shared/format/path';

type StatusFilter = 'all' | BackupStatus;

@Component({
  selector: 'app-history',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    LucideAngularModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    StateMessageComponent,
    MaskPathPipe,
    MaskPathsPipe
  ],
  templateUrl: './history.component.html'
})
export class HistoryComponent implements OnInit {
  private backupService = inject(BackupService);

  readonly refreshIcon = RefreshCw;
  readonly searchIcon = Search;
  readonly arrowIcon = ArrowRight;

  readonly filters: { value: StatusFilter; label: string }[] = [
    { value: 'all', label: 'Todos' },
    { value: 'CONCLUIDO', label: STATUS_LABELS.CONCLUIDO },
    { value: 'FALHA', label: STATUS_LABELS.FALHA },
    { value: 'CANCELADO', label: STATUS_LABELS.CANCELADO },
    { value: 'EM_ANDAMENTO', label: STATUS_LABELS.EM_ANDAMENTO }
  ];

  historyRecords = signal<BackupRecord[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);
  filter = signal<StatusFilter>('all');
  search = signal('');

  filteredRecords = computed(() => {
    const status = this.filter();
    // A busca considera apenas o que esta visivel (caminhos mascarados).
    const term = this.search().trim().toLowerCase();
    return this.historyRecords().filter(r =>
      (status === 'all' || r.status === status) &&
      (!term || `${maskPath(r.sourcePath)} ${maskPath(r.destinationPath)}`.toLowerCase().includes(term))
    );
  });

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
        this.error.set(apiErrorMessage(err, 'Não foi possível carregar o histórico.'));
        this.isLoading.set(false);
      }
    });
  }
}
