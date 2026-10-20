import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BackupService, toBackupEvent } from './backup.service';

describe('BackupService history', () => {
  let service: BackupService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(BackupService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('should request only the newest records when a limit is given', () => {
    let received: unknown[] = [];
    service.getHistory(5).subscribe(records => (received = records));

    const req = controller.expectOne(r => r.url === '/api/backup/history');
    expect(req.request.params.get('limit')).toBe('5');
    req.flush({ items: [{ sourcePath: 'a', destinationPath: 'b', status: 'CONCLUIDO' }] });

    expect(received).toHaveLength(1);
  });

  it('should not send a limit when none is given', () => {
    service.getHistory().subscribe();

    const req = controller.expectOne(r => r.url === '/api/backup/history');
    expect(req.request.params.has('limit')).toBe(false);
    req.flush({ items: [] });
  });

  it('should read totals from the stats endpoint', () => {
    let completed = 0;
    service.getStats().subscribe(stats => (completed = stats.completedBackups));

    controller.expectOne('/api/backup/history/stats').flush({ completedBackups: 1234, failedBackups: 2 });

    expect(completed).toBe(1234);
  });
});

describe('toBackupEvent', () => {
  it('should map the backend progress payload', () => {
    const evt = toBackupEvent(
      'progress',
      '{"percent":42,"currentFile":"a.txt","processedFiles":4,"totalFiles":10,"taskId":"7"}'
    );
    expect(evt).toEqual({
      type: 'progress', taskId: 7, percent: 42, currentFile: 'a.txt', processedFiles: 4, totalFiles: 10
    });
  });

  it('should map control events', () => {
    expect(toBackupEvent('control', '{"type":"complete","taskId":7,"status":"CONCLUIDO","timestamp":1}')).toEqual({
      type: 'control', taskId: 7, action: 'complete', status: 'CONCLUIDO'
    });
  });

  it('should ignore malformed payloads', () => {
    expect(toBackupEvent('progress', 'not-json')).toBeNull();
  });
});
