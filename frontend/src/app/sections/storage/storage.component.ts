import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, HardDrive, TriangleAlert, RefreshCw } from 'lucide-angular';
import { StorageService, DriveInfo } from '../../core/services/storage.service';
import { apiErrorMessage } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StateMessageComponent } from '../../shared/ui/state-message.component';

@Component({
  selector: 'app-storage',
  imports: [DecimalPipe, LucideAngularModule, PageHeaderComponent, StateMessageComponent],
  templateUrl: './storage.component.html'
})
export class StorageComponent implements OnInit {
  private storageService = inject(StorageService);

  readonly driveIcon = HardDrive;
  readonly alertIcon = TriangleAlert;
  readonly refreshIcon = RefreshCw;

  drives = signal<DriveInfo[]>([]);
  isLoading = signal(true);
  forbidden = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadStorageData();
  }

  loadStorageData(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.forbidden.set(false);
    this.storageService.getStorageStats().subscribe({
      next: drives => {
        this.drives.set(drives);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.forbidden.set(true);
          this.error.set('O monitoramento de discos é exclusivo para administradores.');
        } else {
          this.error.set(apiErrorMessage(err, 'Não foi possível carregar os dados de disco.'));
        }
        this.isLoading.set(false);
      }
    });
  }
}
