import { EventStreamParser, ServerSentEvent } from './event-stream';

describe('EventStreamParser', () => {
  function parse(...chunks: string[]): ServerSentEvent[] {
    const events: ServerSentEvent[] = [];
    const parser = new EventStreamParser(evt => events.push(evt));
    chunks.forEach(chunk => parser.push(chunk));
    return events;
  }

  it('should emit named events', () => {
    expect(parse('event:progress\ndata:{"percent":10}\n\n')).toEqual([
      { event: 'progress', data: '{"percent":10}' }
    ]);
  });

  it('should default to the message event and join multiple data lines', () => {
    expect(parse('data: a\ndata: b\n\n')).toEqual([{ event: 'message', data: 'a\nb' }]);
  });

  it('should handle events split across chunks', () => {
    expect(parse('event:con', 'trol\r\ndata:{"taskId":1}\r\n', '\r\n')).toEqual([
      { event: 'control', data: '{"taskId":1}' }
    ]);
  });

  it('should ignore comments', () => {
    expect(parse(':keep-alive\n\n')).toEqual([]);
  });
});
