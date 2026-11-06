import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Observable, lastValueFrom } from 'rxjs';
import { CredentialsService } from './credentials.service';
import { Card, Issue, Snapshot } from './models';
import { SessionApi } from './session-api.service';
import { EVENT_SOURCE_FACTORY, EventSourceLike, RECONNECT_DELAY_MS } from './session-events';

export type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'unauthorized' | 'not-found';

const CLOSED = 2;

/**
 * State of one session page: the latest snapshot pushed via SSE plus commands sent via REST.
 * Commands never update the state themselves; the resulting snapshot arrives through the stream.
 */
@Injectable()
export class SessionStore implements OnDestroy {
  private readonly api = inject(SessionApi);
  private readonly credentials = inject(CredentialsService);
  private readonly openEventSource = inject(EVENT_SOURCE_FACTORY);
  private readonly reconnectDelay = inject(RECONNECT_DELAY_MS);

  private sessionId: string | null = null;
  private source: EventSourceLike | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly _snapshot = signal<Snapshot | null>(null);
  private readonly _status = signal<ConnectionStatus>('idle');
  private readonly _error = signal<string | null>(null);
  private readonly _issues = signal<Issue[]>([]);
  private readonly nextIssuePage = signal<number | null>(null);

  readonly snapshot = this._snapshot.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();
  readonly issues = this._issues.asReadonly();

  readonly me = computed(() => this._snapshot()?.me ?? null);
  readonly state = computed(() => this._snapshot()?.state ?? null);
  readonly isScrumMaster = computed(() => this.me()?.role === 'SCRUM_MASTER');
  readonly canVote = computed(() => this.state() === 'VOTING' && this.me()?.role === 'DEVELOPER');
  readonly allVoted = computed(() => this._snapshot()?.allVoted === true);
  readonly hasMoreIssues = computed(() => this.nextIssuePage() !== null);

  connect(sessionId: string): void {
    this.disconnect();
    this.sessionId = sessionId;
    const credentials = this.credentials.get(sessionId);
    if (!credentials) {
      this._status.set('unauthorized');
      return;
    }
    this._status.set('connecting');
    this.open(credentials.token);
  }

  disconnect(): void {
    this.clearRetry();
    this.source?.close();
    this.source = null;
    this._status.set('idle');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  async join(sessionId: string, name: string): Promise<boolean> {
    try {
      const credentials = await lastValueFrom(this.api.join(sessionId, name));
      this.credentials.save(credentials);
      this._error.set(null);
      this.connect(sessionId);
      return true;
    } catch (e) {
      this._error.set(errorMessage(e));
      return false;
    }
  }

  selectIssue(issueIid: number): Promise<boolean> {
    return this.run((id) => this.api.selectIssue(id, issueIid));
  }

  startRound(): Promise<boolean> {
    return this.run((id) => this.api.startRound(id));
  }

  vote(card: Card): Promise<boolean> {
    return this.run((id) => this.api.vote(id, card));
  }

  reveal(): Promise<boolean> {
    return this.run((id) => this.api.reveal(id));
  }

  restart(): Promise<boolean> {
    return this.run((id) => this.api.restart(id));
  }

  confirm(card: Card): Promise<boolean> {
    return this.run((id) => this.api.confirm(id, card));
  }

  async leave(): Promise<boolean> {
    const sessionId = this.sessionId;
    const ok = await this.run((id) => this.api.leave(id));
    if (ok && sessionId) {
      this.credentials.clear(sessionId);
      this.disconnect();
      this._snapshot.set(null);
    }
    return ok;
  }

  loadIssues(): Promise<boolean> {
    return this.fetchIssues(1, false);
  }

  loadMoreIssues(): Promise<boolean> {
    const next = this.nextIssuePage();
    return next === null ? Promise.resolve(false) : this.fetchIssues(next, true);
  }

  clearError(): void {
    this._error.set(null);
  }

  private async fetchIssues(page: number, append: boolean): Promise<boolean> {
    if (!this.sessionId) {
      return false;
    }
    try {
      const result = await lastValueFrom(this.api.issues(this.sessionId, page));
      this._issues.update((current) => (append ? [...current, ...result.issues] : result.issues));
      this.nextIssuePage.set(result.nextPage);
      return true;
    } catch (e) {
      this._error.set(errorMessage(e));
      return false;
    }
  }

  private async run(command: (sessionId: string) => Observable<void>): Promise<boolean> {
    if (!this.sessionId) {
      return false;
    }
    try {
      await lastValueFrom(command(this.sessionId), { defaultValue: undefined });
      this._error.set(null);
      return true;
    } catch (e) {
      this._error.set(errorMessage(e));
      return false;
    }
  }

  private open(token: string): void {
    const sessionId = this.sessionId!;
    const source = this.openEventSource(
      `/api/sessions/${encodeURIComponent(sessionId)}/events?token=${encodeURIComponent(token)}`,
    );
    this.source = source;
    source.onopen = () => this._status.set('open');
    source.addEventListener('snapshot', (event) => this._snapshot.set(JSON.parse(event.data) as Snapshot));
    source.onerror = () => this.onStreamError(source, token);
  }

  /**
   * Network drops are retried by the browser (readyState CONNECTING). A non-200 answer makes it give up
   * (CLOSED); then a plain GET tells whether the token/session is gone or the backend is just unavailable.
   */
  private onStreamError(source: EventSourceLike, token: string): void {
    if (source !== this.source) {
      return;
    }
    this._status.set('reconnecting');
    if (source.readyState !== CLOSED) {
      return;
    }
    const sessionId = this.sessionId!;
    this.api.snapshot(sessionId).subscribe({
      next: (snapshot) => {
        if (source === this.source) {
          this._snapshot.set(snapshot);
          this.scheduleReopen(token);
        }
      },
      error: (e: unknown) => {
        if (source !== this.source) {
          return;
        }
        const status = e instanceof HttpErrorResponse ? e.status : 0;
        if (status === 401 || status === 404) {
          this.credentials.clear(sessionId);
          this.disconnect();
          this._snapshot.set(null);
          this._status.set(status === 401 ? 'unauthorized' : 'not-found');
        } else {
          this.scheduleReopen(token);
        }
      },
    });
  }

  private scheduleReopen(token: string): void {
    this.clearRetry();
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.source?.close();
      this.open(token);
    }, this.reconnectDelay);
  }

  private clearRetry(): void {
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}

export function errorMessage(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return 'Server nicht erreichbar';
    }
    const message = (e.error as { message?: unknown } | null)?.message;
    return typeof message === 'string' && message ? message : `Fehler ${e.status}`;
  }
  return 'Unerwarteter Fehler';
}
