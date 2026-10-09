import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, HardDrive, AlertTriangle } from 'lucide-angular';
import { StorageService, DriveInfo } from '../../core/services/storage.service'

@Component({
  selector: 'app-storage',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './storage.component.html'
})
export class StorageComponent implements OnInit {
  private storageService = inject(StorageService);

  readonly driveIcon = HardDrive;
  readonly alertIcon = AlertTriangle;

  drives = signal<DriveInfo[]>([]);

  ngOnInit(): void {
    this.loadStorageData();
  }

  loadStorageData(): void {
    // Tipamos explicitamente 'data' como DriveInfo[] e 'err' como any ou Error
    this.storageService.getStorageStats().subscribe({
      next: (data: DriveInfo[]) => {
        this.drives.set(data);
      },
      error: (err: Error) => {
        console.error('Erro ao buscar dados de disco:', err.message);
      }
    });
  }
}
