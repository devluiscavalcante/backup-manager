import { Observable } from 'rxjs';

/** Evento Server-Sent Events ja separado em nome e dados. */
export interface ServerSentEvent {
  event: string;
  data: string;
}

/** Evento sintetico emitido quando a conexao do stream foi aceita pelo servidor. */
export const STREAM_OPEN = 'open';

/**
 * Consome um endpoint text/event-stream via fetch.
 * Diferente de EventSource, permite enviar cabecalhos (Authorization) e entrega eventos nomeados.
 */
export function eventStream(url: string, headers: Record<string, string>): Observable<ServerSentEvent> {
  return new Observable<ServerSentEvent>(subscriber => {
    const controller = new AbortController();

    (async () => {
      const response = await fetch(url, {
        headers: { Accept: 'text/event-stream', ...headers },
        signal: controller.signal
      });
      if (!response.ok || !response.body) {
        throw new Error(`Falha ao abrir stream de eventos (HTTP ${response.status})`);
      }

      subscriber.next({ event: STREAM_OPEN, data: '' });

      const parser = new EventStreamParser(evt => subscriber.next(evt));
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        parser.push(value);
      }
      subscriber.complete();
    })().catch(err => {
      if (!controller.signal.aborted) {
        subscriber.error(err);
      }
    });

    return () => controller.abort();
  });
}

/** Parser incremental do formato text/event-stream (campos event/data, eventos separados por linha em branco). */
export class EventStreamParser {
  private buffer = '';
  private eventName = '';
  private dataLines: string[] = [];

  constructor(private readonly emit: (event: ServerSentEvent) => void) {}

  push(chunk: string): void {
    this.buffer += chunk;
    const lines = this.buffer.split(/\r\n|\r|\n/);
    this.buffer = lines.pop() ?? '';
    for (const line of lines) {
      this.processLine(line);
    }
  }

  private processLine(line: string): void {
    if (line === '') {
      if (this.dataLines.length > 0) {
        this.emit({ event: this.eventName || 'message', data: this.dataLines.join('\n') });
      }
      this.eventName = '';
      this.dataLines = [];
      return;
    }
    if (line.startsWith(':')) {
      return;
    }

    const separator = line.indexOf(':');
    const field = separator === -1 ? line : line.slice(0, separator);
    let value = separator === -1 ? '' : line.slice(separator + 1);
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }

    if (field === 'event') {
      this.eventName = value;
    } else if (field === 'data') {
      this.dataLines.push(value);
    }
  }
}
