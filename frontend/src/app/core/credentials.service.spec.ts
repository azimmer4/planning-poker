import { TestBed } from '@angular/core/testing';
import { CredentialsService } from './credentials.service';

describe('CredentialsService', () => {
  let service: CredentialsService;

  beforeEach(() => {
    localStorage.clear();
    service = TestBed.inject(CredentialsService);
  });

  it('remembers credentials per session', () => {
    service.save({ sessionId: 's1', participantId: 'p1', token: 't1' });
    service.save({ sessionId: 's2', participantId: 'p2', token: 't2' });

    expect(service.get('s1')).toEqual({ sessionId: 's1', participantId: 'p1', token: 't1' });
    expect(service.get('s2')?.token).toBe('t2');
  });

  it('survives a reload (localStorage)', () => {
    service.save({ sessionId: 's1', participantId: 'p1', token: 't1' });

    expect(new CredentialsService().get('s1')?.token).toBe('t1');
  });

  it('returns null for unknown sessions', () => {
    expect(service.get('nope')).toBeNull();
  });

  it('clears only the given session', () => {
    service.save({ sessionId: 's1', participantId: 'p1', token: 't1' });
    service.save({ sessionId: 's2', participantId: 'p2', token: 't2' });

    service.clear('s1');

    expect(service.get('s1')).toBeNull();
    expect(service.get('s2')).not.toBeNull();
  });

  it('ignores corrupted entries', () => {
    localStorage.setItem('planning-poker.session.s1', '{not json');

    expect(service.get('s1')).toBeNull();
  });
});
