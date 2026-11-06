import { EventSourceLike } from '../core/session-events';
import { Snapshot } from '../core/models';

/** In-memory EventSource replacement; tests drive it via open/emit/fail. */
export class FakeEventSource implements EventSourceLike {
  static instances: FakeEventSource[] = [];

  readyState = 0;
  closed = false;
  onopen: ((ev: Event) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;
  private listeners = new Map<string, ((ev: MessageEvent) => void)[]>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  static reset(): void {
    FakeEventSource.instances = [];
  }

  static latest(): FakeEventSource {
    const last = FakeEventSource.instances.at(-1);
    if (!last) {
      throw new Error('no EventSource opened');
    }
    return last;
  }

  addEventListener(type: string, listener: (ev: MessageEvent) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  open(): void {
    this.readyState = 1;
    this.onopen?.(new Event('open'));
  }

  emitSnapshot(snapshot: Snapshot): void {
    const event = new MessageEvent('snapshot', { data: JSON.stringify(snapshot) });
    (this.listeners.get('snapshot') ?? []).forEach((l) => l(event));
  }

  /** Simulates a dropped connection; `fatal` = server answered non-200, browser gives up (CLOSED). */
  fail(fatal: boolean): void {
    this.readyState = fatal ? 2 : 0;
    this.onerror?.(new Event('error'));
  }
}
