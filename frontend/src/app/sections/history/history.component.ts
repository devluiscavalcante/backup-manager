import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, History, CheckCircle, CircleX, ArrowRight } from 'lucide-angular';
import { BackupService, BackupRecord } from '../../core/services/backup.service';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './history.component.html'
})
export class HistoryComponent implements OnInit {

  readonly historyIcon = History;
  readonly successIcon = CheckCircle;
  readonly errorIcon = CircleX;
  readonly arrowIcon = ArrowRight;

  historyRecords = signal<BackupRecord[]>([]);
  isLoading = signal(true);

  constructor(private backupService: BackupService) {}

  ngOnInit() {
    this.loadHistory();
  }

  loadHistory() {
    this.isLoading.set(true);
    this.backupService.getHistory().subscribe({
      next: (data: BackupRecord[]) => {
        this.historyRecords.set(data);
        this.isLoading.set(false);
      },
      error: (err: any) => {
        console.error('Erro ao carregar histórico', err);
        this.isLoading.set(false);
      }
    });
  }
}
