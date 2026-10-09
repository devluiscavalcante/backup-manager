import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { CollectionResponse } from '../api/api.models';

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
  private readonly apiUrl = '/api/system/storage';

  getStorageStats(): Observable<DriveInfo[]> {
    return this.http.get<CollectionResponse<DriveInfo>>(this.apiUrl).pipe(map(res => res.items));
  }
}
