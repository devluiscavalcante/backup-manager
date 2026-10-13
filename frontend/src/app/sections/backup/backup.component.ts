import { Component, signal, computed, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Trash2, Play, Pause, CircleX, ArrowRight, FolderInput, FolderOutput } from 'lucide-angular';
import { Observable, Subscription, forkJoin } from 'rxjs';
import { BackupService, BackupStreamEvent } from '../../core/services/backup.service';
import { OperationResponse, apiErrorMessage } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { MaskPathPipe } from '../../shared/format/mask-path.pipe';
import { baseName, maskPathsInText } from '../../shared/format/path';

type TaskOutcome = 'running' | 'done' | 'failed';

/** Origem e destino de um backup; o backend recebe as duas listas pareadas por indice. */
export interface BackupPair {
  source: string;
  destination: string;
}

const STREAM_OPEN_TIMEOUT_MS = 10_000;

@Component({
  selector: 'app-backup',
  imports: [FormsModule, LucideAngularModule, PageHeaderComponent, MaskPathPipe],
  templateUrl: './backup.component.html'
})
export class BackupComponent implements OnDestroy {
  private backupService = inject(BackupService);

  readonly plusIcon = Plus;
  readonly trashIcon = Trash2;
  readonly playIcon = Play;
  readonly pauseIcon = Pause;
  readonly cancelIcon = CircleX;
  readonly arrowIcon = ArrowRight;
  readonly sourceIcon = FolderInput;
  readonly destinationIcon = FolderOutput;

  pairs = signal<BackupPair[]>([]);
  newSource = '';
  newDestination = '';
  pairError = signal<string | null>(null);

  isBackingUp = signal(false);
  isPaused = signal(false);
  isControlBusy = signal(false);
  currentStatus = signal('');
  activeTaskIds = signal<number[]>([]);

  /** Percentual e situacao de cada tarefa iniciada nesta execucao. */
  private taskPercent = signal<Record<number, number>>({});
  private taskOutcome = signal<Record<number, TaskOutcome>>({});

  /** Progresso agregado: media simples das tarefas da execucao. */
  progress = computed(() => {
    const ids = this.activeTaskIds();
    if (ids.length === 0) return 0;
    const percents = this.taskPercent();
    const total = ids.reduce((sum, id) => sum + (percents[id] ?? 0), 0);
    return Math.round(total / ids.length);
  });

  private streamSubscription?: Subscription;
  private resetTimer?: ReturnType<typeof setTimeout>;
  private openTimer?: ReturnType<typeof setTimeout>;

  canStart = computed(() => this.pairs().length > 0 && !this.isBackingUp());

  addPair() {
    const source = this.newSource.trim();
    const destination = this.newDestination.trim();
    if (!source || !destination) {
      this.pairError.set('Informe a origem e o destino.');
      return;
    }
    if (normalize(source) === normalize(destination)) {
      this.pairError.set('A origem e o destino precisam ser pastas diferentes.');
      return;
    }
    if (this.pairs().some(p => normalize(p.source) === normalize(source) && normalize(p.destination) === normalize(destination))) {
      this.pairError.set('Esse par já está na lista.');
      return;
    }

    this.pairs.update(list => [...list, { source, destination }]);
    this.newSource = '';
    this.newDestination = '';
    this.pairError.set(null);
  }

  removePair(index: number) {
    this.pairs.update(list => list.filter((_, i) => i !== index));
  }

  startBackup() {
    if (!this.canStart()) return;

    clearTimeout(this.resetTimer);
    this.isBackingUp.set(true);
    this.isPaused.set(false);
    this.activeTaskIds.set([]);
    this.taskPercent.set({});
    this.taskOutcome.set({});
    this.currentStatus.set('Validando pastas...');

    // O stream e aberto antes do POST para nao perder eventos de backups rapidos.
    this.streamSubscription?.unsubscribe();
    clearTimeout(this.openTimer);
    this.openTimer = setTimeout(
      () => this.finalizeProcess('O servidor de progresso não respondeu. Nenhum backup foi iniciado.', 0),
      STREAM_OPEN_TIMEOUT_MS
    );
    this.streamSubscription = this.backupService.progressEvents().subscribe({
      next: evt => this.handleStreamEvent(evt),
      error: () => {
        if (this.activeTaskIds().length === 0) {
          this.finalizeProcess('Não foi possível conectar ao servidor de progresso.');
        } else {
          this.finalizeProcess('Conexão de progresso perdida; o backup continua no servidor. Consulte o histórico.');
        }
      }
    });
  }

  togglePause() {
    const resume = this.isPaused();
    this.runControl(
      id => (resume ? this.backupService.resumeBackup(id) : this.backupService.pauseBackup(id)),
      () => {
        this.isPaused.set(!resume);
        this.currentStatus.set(resume ? 'Retomando backup...' : 'Backup pausado.');
      },
      resume ? 'Não foi possível retomar o backup.' : 'Não foi possível pausar o backup.'
    );
  }

  cancelBackup() {
    this.runControl(
      id => this.backupService.cancelBackup(id),
      () => this.finalizeProcess('Backup cancelado.'),
      'Não foi possível cancelar o backup.'
    );
  }

  private handleStreamEvent(evt: BackupStreamEvent) {
    switch (evt.type) {
      case 'open':
        clearTimeout(this.openTimer);
        this.submitBackupRequest();
        return;
      case 'progress':
        if (!this.isTracked(evt.taskId)) return;
        this.taskPercent.update(p => ({ ...p, [evt.taskId]: evt.percent }));
        if (!this.isPaused()) {
          // Mostra apenas o nome do arquivo, nunca o caminho completo.
          const file = baseName(evt.currentFile);
          this.currentStatus.set(file ? `Copiando ${file}` : 'Processando arquivos...');
        }
        return;
      case 'control':
        if (!this.isTracked(evt.taskId)) return;
        if (evt.status === 'CONCLUIDO') {
          this.markTask(evt.taskId, 'done');
        } else if (evt.status === 'FALHA') {
          this.markTask(evt.taskId, 'failed');
        }
        return;
      case 'error':
        if (evt.message) {
          this.currentStatus.set(maskPathsInText(evt.message));
        }
        return;
      case 'complete':
        return;
    }
  }

  private submitBackupRequest() {
    const pairs = this.pairs();
    this.backupService.startBackup(pairs.map(p => p.source), pairs.map(p => p.destination)).subscribe({
      next: (res: OperationResponse) => {
        const ids = res.taskIds ?? [];
        this.activeTaskIds.set(ids);
        // Eventos podem ter chegado antes da resposta do POST; reaplica o que ja foi concluido.
        this.checkAllFinished();
        if (ids.length === 0) {
          this.finalizeProcess(res.message || 'Nenhuma tarefa foi iniciada.');
        }
      },
      error: err => this.finalizeProcess(maskPathsInText(apiErrorMessage(err, 'Não foi possível iniciar o backup.')), 0)
    });
  }

  private runControl(
    action: (taskId: number) => Observable<OperationResponse>,
    onSuccess: () => void,
    failureMessage: string
  ) {
    const running = this.activeTaskIds().filter(id => (this.taskOutcome()[id] ?? 'running') === 'running');
    if (running.length === 0 || this.isControlBusy()) return;

    this.isControlBusy.set(true);
    forkJoin(running.map(action)).subscribe({
      next: () => {
        this.isControlBusy.set(false);
        onSuccess();
      },
      error: err => {
        this.isControlBusy.set(false);
        this.currentStatus.set(maskPathsInText(apiErrorMessage(err, failureMessage)));
      }
    });
  }

  private isTracked(taskId: number): boolean {
    const ids = this.activeTaskIds();
    // Antes da resposta do POST os IDs ainda sao desconhecidos: aceita e filtra depois.
    return ids.length === 0 || ids.includes(taskId);
  }

  private markTask(taskId: number, outcome: TaskOutcome) {
    this.taskOutcome.update(o => ({ ...o, [taskId]: outcome }));
    if (outcome === 'done') {
      this.taskPercent.update(p => ({ ...p, [taskId]: 100 }));
    }
    this.checkAllFinished();
  }

  private checkAllFinished() {
    const ids = this.activeTaskIds();
    if (ids.length === 0) return;

    const outcomes = this.taskOutcome();
    if (ids.some(id => (outcomes[id] ?? 'running') === 'running')) return;

    const failed = ids.filter(id => outcomes[id] === 'failed').length;
    this.finalizeProcess(
      failed === 0
        ? 'Backup concluído com sucesso.'
        : `${failed} de ${ids.length} backup(s) falharam. Consulte o histórico.`
    );
  }

  private finalizeProcess(msg: string, resetDelayMs = 4000) {
    this.currentStatus.set(msg);
    clearTimeout(this.openTimer);
    this.streamSubscription?.unsubscribe();
    this.streamSubscription = undefined;

    clearTimeout(this.resetTimer);
    this.resetTimer = setTimeout(() => {
      this.isBackingUp.set(false);
      this.isPaused.set(false);
      this.activeTaskIds.set([]);
      this.taskPercent.set({});
      this.taskOutcome.set({});
    }, resetDelayMs);
  }

  ngOnDestroy() {
    clearTimeout(this.resetTimer);
    clearTimeout(this.openTimer);
    this.streamSubscription?.unsubscribe();
  }
}

function normalize(path: string): string {
  return path.replace(/[\\/]+$/, '').toLowerCase();
}
