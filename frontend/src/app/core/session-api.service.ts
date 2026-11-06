import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Card, CreateSessionRequest, Credentials, IssuePage, Snapshot } from './models';

export const ISSUES_PER_PAGE = 20;

/** REST commands of the backend; the token header is added by participantTokenInterceptor. */
@Injectable({ providedIn: 'root' })
export class SessionApi {
  private readonly http = inject(HttpClient);

  create(request: CreateSessionRequest): Observable<Credentials> {
    return this.http.post<Credentials>('/api/sessions', request);
  }

  join(sessionId: string, name: string): Observable<Credentials> {
    return this.http.post<Credentials>(`${url(sessionId)}/join`, { name });
  }

  snapshot(sessionId: string): Observable<Snapshot> {
    return this.http.get<Snapshot>(url(sessionId));
  }

  issues(sessionId: string, page: number): Observable<IssuePage> {
    return this.http.get<IssuePage>(`${url(sessionId)}/issues`, { params: { page, perPage: ISSUES_PER_PAGE } });
  }

  selectIssue(sessionId: string, issueIid: number): Observable<void> {
    return this.http.put<void>(`${url(sessionId)}/issue`, { issueIid });
  }

  startRound(sessionId: string): Observable<void> {
    return this.http.post<void>(`${url(sessionId)}/rounds`, null);
  }

  vote(sessionId: string, card: Card): Observable<void> {
    return this.http.put<void>(`${url(sessionId)}/votes`, { card });
  }

  reveal(sessionId: string): Observable<void> {
    return this.http.post<void>(`${url(sessionId)}/reveal`, null);
  }

  restart(sessionId: string): Observable<void> {
    return this.http.post<void>(`${url(sessionId)}/rounds/restart`, null);
  }

  confirm(sessionId: string, card: Card): Observable<void> {
    return this.http.post<void>(`${url(sessionId)}/confirm`, { card });
  }

  leave(sessionId: string): Observable<void> {
    return this.http.post<void>(`${url(sessionId)}/leave`, null);
  }
}

function url(sessionId: string): string {
  return `/api/sessions/${encodeURIComponent(sessionId)}`;
}
