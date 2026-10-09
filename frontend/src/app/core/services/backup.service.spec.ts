import { toBackupEvent } from './backup.service';

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
