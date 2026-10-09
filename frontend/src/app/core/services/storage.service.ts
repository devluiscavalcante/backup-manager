import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DriveInfo {
  driveLetter: string;
  totalSpaceGB: number;
  freeSpaceGB: number;
  usedSpaceGB: number;
  usagePercent: number;
  isCritical: boolean;
}

@Injectable({ providedIn: 'root' })
export class StorageService {
  private http = inject(HttpClient);
  private readonly API = 'http://localhost:8080/api/system/storage';

  getStorageStats(): Observable<DriveInfo[]> {
    return this.http.get<DriveInfo[]>(this.API);
  }
}
