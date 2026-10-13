import { Pipe, PipeTransform } from '@angular/core';
import { maskPath, maskPathsInText } from './path';

/** Exibe um caminho reduzido a raiz e ultimo segmento. */
@Pipe({ name: 'maskPath' })
export class MaskPathPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return maskPath(value);
  }
}

/** Mascara os caminhos contidos em um texto livre. */
@Pipe({ name: 'maskPaths' })
export class MaskPathsPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return maskPathsInText(value);
  }
}
