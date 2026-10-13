import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import {
  LucideAngularModule,
  Activity,
  CircleCheck,
  CircleX,
  Clock,
  Play,
  ArrowRight,
  ChevronRight
} from 'lucide-angular';
import { BackupRecord, BackupService } from '../../core/services/backup.service';
import { DriveInfo, StorageService } from '../../core/services/storage.service';
import { apiErrorMessage } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { StateMessageComponent } from '../../shared/ui/state-message.component';
import { MaskPathPipe } from '../../shared/format/mask-path.pipe';

const RECENT_LIMIT = 5;

@Component({
  selector: 'app-dashboard',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    LucideAngularModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    StateMessageComponent,
    MaskPathPipe
  ],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private backupService = inject(BackupService);
  private storageService = inject(StorageService);

  readonly activityIcon = Activity;
  readonly doneIcon = CircleCheck;
  readonly failIcon = CircleX;
  readonly clockIcon = Clock;
  readonly playIcon = Play;
  readonly arrowIcon = ArrowRight;
  readonly chevronIcon = ChevronRight;

  history = signal<BackupRecord[]>([]);
  historyLoading = signal(true);
  historyError = signal<string | null>(null);

  activeCount = signal<number | null>(null);

  drives = signal<DriveInfo[]>([]);
  storageLoading = signal(true);
  storageForbidden = signal(false);
  storageError = signal<string | null>(null);

  recent = computed(() => this.history().slice(0, RECENT_LIMIT));
  completedCount = computed(() => this.history().filter(r => r.status === 'CONCLUIDO').length);
  failedCount = computed(() => this.history().filter(r => r.status === 'FALHA').length);
  lastRun = computed(() => this.history()[0]?.startedAt ?? null);

  ngOnInit(): void {
    this.backupService.getHistory().subscribe({
      next: records => {
        this.history.set([...records].sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? '')));
        this.historyLoading.set(false);
      },
      error: (err: unknown) => {
        this.historyError.set(apiErrorMessage(err, 'Não foi possível carregar o histórico.'));
        this.historyLoading.set(false);
      }
    });

    this.backupService.getActiveTasks().subscribe({
      next: tasks => this.activeCount.set(tasks.length),
      error: () => this.activeCount.set(null)
    });

    this.storageService.getStorageStats().subscribe({
      next: drives => {
        this.drives.set(drives);
        this.storageLoading.set(false);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.storageForbidden.set(true);
        } else {
          this.storageError.set(apiErrorMessage(err, 'Não foi possível carregar os discos.'));
        }
        this.storageLoading.set(false);
      }
    });
  }
}
