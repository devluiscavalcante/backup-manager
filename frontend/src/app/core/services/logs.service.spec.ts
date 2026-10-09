import { parseLogLines } from './logs.service';

describe('parseLogLines', () => {
  it('should parse the warnings.log format', () => {
    const entries = parseLogLines(
      '[2026-10-09T14:00:00.123] [WARNING] Acesso negado: C:\\pasta\\arquivo.txt\r\n' +
      '[2026-10-09T14:00:01] [ERROR] Falha ao copiar: D:\\x\n'
    );
    expect(entries).toEqual([
      { timestamp: '2026-10-09T14:00:00.123', level: 'warning', message: 'Acesso negado: C:\\pasta\\arquivo.txt' },
      { timestamp: '2026-10-09T14:00:01', level: 'error', message: 'Falha ao copiar: D:\\x' }
    ]);
  });

  it('should keep unstructured lines as info', () => {
    expect(parseLogLines('Nenhum alerta encontrado\n\n')).toEqual([
      { timestamp: null, level: 'info', message: 'Nenhum alerta encontrado' }
    ]);
  });
});
