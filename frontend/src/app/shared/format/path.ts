/**
 * Mascaramento de caminhos para exibicao: a interface nunca mostra a estrutura completa de pastas,
 * apenas a raiz (unidade) e o ultimo segmento, o suficiente para o usuario reconhecer o item.
 */

const ELLIPSIS = '…';

// Caminho Windows (C:\...), UNC (\\servidor\...) ou Unix absoluto com pelo menos dois segmentos.
const PATH_IN_TEXT = /(?:(?<!\w)[A-Za-z]:[\\/]|\\\\)[^\r\n"'<>|]*|(?<![\w:/.])\/(?:[\w.-]+\/)+[\w.-]*/g;

/** "C:\Users\ana\Documents" -> "C:\…\Documents". Caminhos curtos (ate dois segmentos) ficam como estao. */
export function maskPath(path: string | null | undefined): string {
  const raw = (path ?? '').trim();
  const value = raw.replace(/[\\/]+$/, '');
  if (!value) return '';

  const isUnc = value.startsWith('\\\\');
  const separator = raw.includes('\\') ? '\\' : '/';
  const segments = value.split(/[\\/]+/).filter(Boolean);
  const last = segments[segments.length - 1] ?? '';

  if (isUnc) {
    return `\\\\${ELLIPSIS}${separator}${last}`;
  }

  const drive = /^[A-Za-z]:$/.test(segments[0] ?? '') ? segments[0] : '';
  const root = drive ? `${drive}${separator}` : value.startsWith('/') ? '/' : '';
  const body = drive ? segments.slice(1) : segments;

  if (body.length <= 1) {
    return `${root}${body.join(separator)}`;
  }
  return `${root}${ELLIPSIS}${separator}${last}`;
}

/** Nome do arquivo ou pasta final de um caminho. */
export function baseName(path: string | null | undefined): string {
  const segments = (path ?? '').split(/[\\/]+/).filter(Boolean);
  return segments[segments.length - 1] ?? '';
}

/** Mascara todos os caminhos encontrados em um texto livre (mensagens de log e de erro). */
export function maskPathsInText(text: string | null | undefined): string {
  return (text ?? '').replace(PATH_IN_TEXT, match => {
    const trimmed = match.replace(/[\s.,;:)\]]+$/, '');
    const suffix = match.slice(trimmed.length);
    return maskPath(trimmed) + suffix;
  });
}
