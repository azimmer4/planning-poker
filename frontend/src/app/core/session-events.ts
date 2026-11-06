import { InjectionToken } from '@angular/core';

/** The subset of the browser EventSource used by the app; replaceable in tests. */
export interface EventSourceLike {
  readonly readyState: number;
  onopen: ((ev: Event) => void) | null;
  onerror: ((ev: Event) => void) | null;
  addEventListener(type: string, listener: (ev: MessageEvent) => void): void;
  close(): void;
}

export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSourceLike>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** Delay before reopening a stream the browser has given up on (e.g. backend restarting behind the proxy). */
export const RECONNECT_DELAY_MS = new InjectionToken<number>('RECONNECT_DELAY_MS', {
  providedIn: 'root',
  factory: () => 2000,
});
