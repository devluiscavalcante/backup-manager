import { baseName, maskPath, maskPathsInText } from './path';

describe('maskPath', () => {
  it('should keep only the drive and the last segment', () => {
    expect(maskPath('C:\\Users\\ana\\Documents')).toBe('C:\\…\\Documents');
    expect(maskPath('D:\\Backups\\2026\\')).toBe('D:\\…\\2026');
  });

  it('should leave short paths untouched', () => {
    expect(maskPath('D:\\Backup')).toBe('D:\\Backup');
    expect(maskPath('E:\\')).toBe('E:\\');
  });

  it('should hide the server of UNC paths', () => {
    expect(maskPath('\\\\nas01\\share\\fotos')).toBe('\\\\…\\fotos');
  });

  it('should mask Unix paths', () => {
    expect(maskPath('/home/ana/docs')).toBe('/…/docs');
  });

  it('should handle empty values', () => {
    expect(maskPath(null)).toBe('');
    expect(maskPath('  ')).toBe('');
  });
});

describe('maskPathsInText', () => {
  it('should mask a path at the end of a log message', () => {
    expect(maskPathsInText('Acesso negado: C:\\Program Files\\App\\config.ini'))
      .toBe('Acesso negado: C:\\…\\config.ini');
  });

  it('should keep trailing punctuation outside the mask', () => {
    expect(maskPathsInText('Origem C:\\Users\\ana\\docs.')).toBe('Origem C:\\…\\docs.');
  });

  it('should not touch URLs or plain text', () => {
    expect(maskPathsInText('Veja https://example.com/a/b')).toBe('Veja https://example.com/a/b');
    expect(maskPathsInText('Backup concluido')).toBe('Backup concluido');
  });
});

describe('baseName', () => {
  it('should return the final segment', () => {
    expect(baseName('C:\\dados\\relatorio.pdf')).toBe('relatorio.pdf');
    expect(baseName('')).toBe('');
  });
});
