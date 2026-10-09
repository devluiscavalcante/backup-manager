import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, HardDrive, AlertTriangle, RefreshCw } from 'lucide-angular';
import { StorageService, DriveInfo } from '../../core/services/storage.service';
import { apiErrorMessage } from '../../core/api/api.models';

@Component({
  selector: 'app-storage',
  imports: [LucideAngularModule],
  templateUrl: './storage.component.html'
})
export class StorageComponent implements OnInit {
  private storageService = inject(StorageService);

  readonly driveIcon = HardDrive;
  readonly alertIcon = AlertTriangle;
  readonly refreshIcon = RefreshCw;

  drives = signal<DriveInfo[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadStorageData();
  }

  loadStorageData(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.storageService.getStorageStats().subscribe({
      next: drives => {
        this.drives.set(drives);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 403
            ? 'Disk monitoring requires an administrator account.'
            : apiErrorMessage(err, 'Nao foi possivel carregar os dados de disco.')
        );
        this.isLoading.set(false);
      }
    });
  }
}
