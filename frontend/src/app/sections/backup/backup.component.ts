import { Component, signal, computed, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Trash2, Play, Folder, ShieldCheck, Zap, XCircle, Pause, RefreshCw } from 'lucide-angular';
import { BackupService, BackupProgress } from '../../core/services/backup.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-backup',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './backup.component.html'
})
export class BackupComponent implements OnDestroy {

  readonly plusIcon = Plus;
  readonly trashIcon = Trash2;
  readonly playIcon = Play;
  readonly folderIcon = Folder;
  readonly shieldIcon = ShieldCheck;
  readonly zapIcon = Zap;
  readonly cancelIcon = XCircle;
  readonly pauseIcon = Pause;
  readonly resumeIcon = RefreshCw;

  sources = signal<string[]>([]);
  destinations = signal<string[]>([]);
  newSource = '';
  newDestination = '';

  isBackingUp = signal(false);
  isPaused = signal(false); // Novo estado para controle de pausa
  progress = signal(0);
  currentStatus = signal('');
  activeTaskIds = signal<number[]>([]);

  private sseSubscription?: Subscription;

  canStart = computed(() =>
    this.sources().length > 0 &&
    this.destinations().length > 0 &&
    this.sources().length === this.destinations().length &&
    !this.isBackingUp()
  );

  constructor(private backupService: BackupService) {}

  addSource() {
    if (this.newSource.trim()) {
      this.sources.update(s => [...s, this.newSource.trim()]);
      this.newSource = '';
    }
  }

  addDestination() {
    if (this.newDestination.trim()) {
      this.destinations.update(d => [...d, this.newDestination.trim()]);
      this.newDestination = '';
    }
  }

  removeSource(index: number) {
    this.sources.update(s => s.filter((_, i) => i !== index));
  }

  removeDestination(index: number) {
    this.destinations.update(d => d.filter((_, i) => i !== index));
  }

  startBackup() {
    if (!this.canStart()) return;

    this.isBackingUp.set(true);
    this.isPaused.set(false);
    this.currentStatus.set('Validando caminhos de segurança...');

    this.backupService.startBackup(this.sources(), this.destinations())
      .subscribe({
        next: (res: any) => {
          this.activeTaskIds.set(res.taskIds || []);
          this.connectToProgressStream();
        },
        error: (err) => {
          this.isBackingUp.set(false);
          this.currentStatus.set(err.error || 'Erro na validação do sistema.');
        }
      });
  }

  togglePause() {
    const tasks = this.activeTaskIds();
    if (tasks.length === 0) return;

    const action = this.isPaused()
      ? this.backupService.resumeBackup(tasks[0])
      : this.backupService.pauseBackup(tasks[0]);

    action.subscribe(() => {
      this.isPaused.update(v => !v);
      this.currentStatus.set(this.isPaused() ? 'Backup pausado pelo usuário' : 'Retomando backup...');
    });
  }

  cancelBackup() {
    const tasks = this.activeTaskIds();
    if (tasks.length === 0) return;

    this.backupService.cancelBackup(tasks[0]).subscribe(() => {
      this.finalizeProcess('Operação cancelada pelo usuário.');
    });
  }

  private connectToProgressStream() {
    this.sseSubscription = this.backupService.getRealTimeProgress().subscribe({
      next: (data: BackupProgress) => { // Tipagem explícita resolve o erro TS7006
        this.progress.set(data.percentage);
        this.currentStatus.set(`Copiando: ${data.currentFile || 'Processando arquivos...'}`);

        if (data.percentage === 100) {
          this.finalizeProcess('Backup concluído com sucesso!');
        }
      },
      error: () => this.finalizeProcess('Conexão perdida, mas o processo continua no servidor.')
    });
  }

  private finalizeProcess(msg: string) {
    this.currentStatus.set(msg);
    setTimeout(() => {
      this.isBackingUp.set(false);
      this.isPaused.set(false);
      this.progress.set(0);
      this.activeTaskIds.set([]);
      this.sseSubscription?.unsubscribe();
    }, 4000);
  }

  ngOnDestroy() {
    this.sseSubscription?.unsubscribe();
  }
}
