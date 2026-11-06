import { Injectable } from '@angular/core';
import { Credentials } from './models';

const PREFIX = 'planning-poker.session.';

/** Keeps the participant token per session in localStorage so a reload re-joins as the same participant. */
@Injectable({ providedIn: 'root' })
export class CredentialsService {
  get(sessionId: string): Credentials | null {
    const raw = localStorage.getItem(PREFIX + sessionId);
    if (!raw) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw) as Partial<Credentials>;
      return parsed.participantId && parsed.token ? { sessionId, participantId: parsed.participantId, token: parsed.token } : null;
    } catch {
      return null;
    }
  }

  save(credentials: Credentials): void {
    localStorage.setItem(PREFIX + credentials.sessionId, JSON.stringify(credentials));
  }

  clear(sessionId: string): void {
    localStorage.removeItem(PREFIX + sessionId);
  }
}
