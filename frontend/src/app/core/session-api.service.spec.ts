import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SessionApi } from './session-api.service';

describe('SessionApi', () => {
  let api: SessionApi;
  let backend: HttpTestingController;
  const creds = { sessionId: 's1', participantId: 'p1', token: 't1' };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SessionApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('creates a session', async () => {
    const result = firstValueFrom(api.create({ projectId: '1', issueIid: 2, name: 'Sarah' }));
    const req = backend.expectOne({ method: 'POST', url: '/api/sessions' });
    expect(req.request.body).toEqual({ projectId: '1', issueIid: 2, name: 'Sarah' });
    req.flush(creds);
    expect(await result).toEqual(creds);
  });

  it('joins a session', async () => {
    const result = firstValueFrom(api.join('s1', 'Tom'));
    const req = backend.expectOne({ method: 'POST', url: '/api/sessions/s1/join' });
    expect(req.request.body).toEqual({ name: 'Tom' });
    req.flush(creds);
    expect(await result).toEqual(creds);
  });

  it('reads the snapshot', () => {
    api.snapshot('s1').subscribe();
    backend.expectOne({ method: 'GET', url: '/api/sessions/s1' }).flush({});
  });

  it('lists GitLab issues page by page', async () => {
    const result = firstValueFrom(api.issues('s1', 2));
    const req = backend.expectOne((r) => r.url === '/api/sessions/s1/issues');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('perPage')).toBe('20');
    req.flush({ issues: [], nextPage: null });
    expect(await result).toEqual({ issues: [], nextPage: null });
  });

  it.each([
    ['selectIssue', () => api.selectIssue('s1', 3), 'PUT', '/api/sessions/s1/issue', { issueIid: 3 }],
    ['startRound', () => api.startRound('s1'), 'POST', '/api/sessions/s1/rounds', null],
    ['vote', () => api.vote('s1', '13'), 'PUT', '/api/sessions/s1/votes', { card: '13' }],
    ['reveal', () => api.reveal('s1'), 'POST', '/api/sessions/s1/reveal', null],
    ['restart', () => api.restart('s1'), 'POST', '/api/sessions/s1/rounds/restart', null],
    ['confirm', () => api.confirm('s1', '☕'), 'POST', '/api/sessions/s1/confirm', { card: '☕' }],
    ['leave', () => api.leave('s1'), 'POST', '/api/sessions/s1/leave', null],
  ] as const)('%s → %s %s', (_name, call, method, url, body) => {
    call().subscribe();
    const req = backend.expectOne({ method, url });
    expect(req.request.body).toEqual(body);
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
