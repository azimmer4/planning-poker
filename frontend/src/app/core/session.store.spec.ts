import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FakeEventSource } from '../testing/fake-event-source';
import { ISSUE, developerView, snapshot } from '../testing/snapshots';
import { CredentialsService } from './credentials.service';
import { EVENT_SOURCE_FACTORY, RECONNECT_DELAY_MS } from './session-events';
import { SessionStore } from './session.store';

describe('SessionStore', () => {
  let store: SessionStore;
  let backend: HttpTestingController;
  let credentials: CredentialsService;

  beforeEach(() => {
    localStorage.clear();
    FakeEventSource.reset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SessionStore,
        { provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => new FakeEventSource(url) },
        { provide: RECONNECT_DELAY_MS, useValue: 1000 },
      ],
    });
    store = TestBed.inject(SessionStore);
    backend = TestBed.inject(HttpTestingController);
    credentials = TestBed.inject(CredentialsService);
    credentials.save({ sessionId: 's1', participantId: 'p-sm', token: 'tok/+=' });
  });

  afterEach(() => backend.verify());

  describe('real-time connection', () => {
    it('opens the event stream with the url-encoded token', () => {
      store.connect('s1');

      expect(FakeEventSource.latest().url).toBe('/api/sessions/s1/events?token=tok%2F%2B%3D');
      expect(store.status()).toBe('connecting');
      FakeEventSource.latest().open();
      expect(store.status()).toBe('open');
    });

    it('without credentials it does not connect', () => {
      store.connect('unknown');

      expect(FakeEventSource.instances).toHaveLength(0);
      expect(store.status()).toBe('unauthorized');
    });

    it('every snapshot event replaces the state', () => {
      store.connect('s1');
      FakeEventSource.latest().emitSnapshot(snapshot());
      expect(store.state()).toBe('ISSUE_SELECTED');

      FakeEventSource.latest().emitSnapshot(snapshot({ state: 'VOTING', roundNumber: 1 }));
      expect(store.state()).toBe('VOTING');
      expect(store.snapshot()?.roundNumber).toBe(1);
    });

    it('a dropped connection is retried by the browser itself', () => {
      store.connect('s1');
      FakeEventSource.latest().open();

      FakeEventSource.latest().fail(false);

      expect(store.status()).toBe('reconnecting');
      expect(FakeEventSource.instances).toHaveLength(1);
    });

    it('a stream the browser gave up on is reopened after a delay when the session still exists', fakeAsync(() => {
      store.connect('s1');
      const first = FakeEventSource.latest();

      first.fail(true);
      backend.expectOne('/api/sessions/s1').flush({ message: 'Bad Gateway' }, { status: 502, statusText: 'Bad Gateway' });
      expect(store.status()).toBe('reconnecting');
      expect(FakeEventSource.instances).toHaveLength(1);

      tick(1000);
      expect(first.closed).toBe(true);
      expect(FakeEventSource.instances).toHaveLength(2);
    }));

    it('a rejected token forgets the credentials', () => {
      store.connect('s1');

      FakeEventSource.latest().fail(true);
      backend.expectOne('/api/sessions/s1').flush({ message: 'nope' }, { status: 401, statusText: 'Unauthorized' });

      expect(store.status()).toBe('unauthorized');
      expect(credentials.get('s1')).toBeNull();
      expect(FakeEventSource.latest().closed).toBe(true);
    });

    it('an unknown session is reported as not found', () => {
      store.connect('s1');

      FakeEventSource.latest().fail(true);
      backend.expectOne('/api/sessions/s1').flush({ message: 'nope' }, { status: 404, statusText: 'Not Found' });

      expect(store.status()).toBe('not-found');
    });

    it('disconnect closes the stream and stops reconnecting', fakeAsync(() => {
      store.connect('s1');
      const source = FakeEventSource.latest();
      source.fail(true);
      backend.expectOne('/api/sessions/s1').flush(null, { status: 502, statusText: 'Bad Gateway' });

      store.disconnect();
      tick(5000);

      expect(source.closed).toBe(true);
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(store.status()).toBe('idle');
    }));
  });

  describe('derived state', () => {
    beforeEach(() => store.connect('s1'));

    it('knows the scrum master', () => {
      FakeEventSource.latest().emitSnapshot(snapshot());
      expect(store.isScrumMaster()).toBe(true);

      FakeEventSource.latest().emitSnapshot(developerView());
      expect(store.isScrumMaster()).toBe(false);
    });

    it('only developers can vote and only while voting', () => {
      FakeEventSource.latest().emitSnapshot(developerView({ state: 'ISSUE_SELECTED' }));
      expect(store.canVote()).toBe(false);

      FakeEventSource.latest().emitSnapshot(developerView({ state: 'VOTING' }));
      expect(store.canVote()).toBe(true);

      FakeEventSource.latest().emitSnapshot(snapshot({ state: 'VOTING' }));
      expect(store.canVote()).toBe(false);
    });

    it('allVoted is only true when the backend says so', () => {
      FakeEventSource.latest().emitSnapshot(snapshot({ state: 'VOTING', allVoted: true }));
      expect(store.allVoted()).toBe(true);

      FakeEventSource.latest().emitSnapshot(developerView({ state: 'VOTING' }));
      expect(store.allVoted()).toBe(false);
    });
  });

  describe('commands', () => {
    beforeEach(() => store.connect('s1'));

    it('sends the vote and resolves true on success', async () => {
      const done = store.vote('8');
      const req = backend.expectOne({ method: 'PUT', url: '/api/sessions/s1/votes' });
      expect(req.request.body).toEqual({ card: '8' });
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(await done).toBe(true);
      expect(store.error()).toBeNull();
    });

    it('shows the backend error message and clears it with the next success', async () => {
      const failed = store.reveal();
      backend
        .expectOne('/api/sessions/s1/reveal')
        .flush({ message: 'Aktion im aktuellen Zustand nicht möglich' }, { status: 409, statusText: 'Conflict' });
      expect(await failed).toBe(false);
      expect(store.error()).toBe('Aktion im aktuellen Zustand nicht möglich');

      const ok = store.reveal();
      backend.expectOne('/api/sessions/s1/reveal').flush(null, { status: 204, statusText: 'No Content' });
      await ok;
      expect(store.error()).toBeNull();
    });

    it('falls back to a generic message when the backend is unreachable', async () => {
      const failed = store.startRound();
      backend.expectOne('/api/sessions/s1/rounds').error(new ProgressEvent('error'));
      await failed;

      expect(store.error()).toBe('Server nicht erreichbar');
    });

    it.each([
      ['selectIssue', (s: SessionStore) => s.selectIssue(4), 'PUT', '/api/sessions/s1/issue'],
      ['startRound', (s: SessionStore) => s.startRound(), 'POST', '/api/sessions/s1/rounds'],
      ['restart', (s: SessionStore) => s.restart(), 'POST', '/api/sessions/s1/rounds/restart'],
      ['confirm', (s: SessionStore) => s.confirm('5'), 'POST', '/api/sessions/s1/confirm'],
    ] as const)('%s calls %s %s', async (_n, call, method, url) => {
      const done = call(store);
      backend.expectOne({ method, url }).flush(null, { status: 204, statusText: 'No Content' });
      expect(await done).toBe(true);
    });

    it('leaving closes the stream and forgets the credentials', async () => {
      const done = store.leave();
      backend.expectOne({ method: 'POST', url: '/api/sessions/s1/leave' }).flush(null, { status: 204, statusText: 'No Content' });
      await done;

      expect(credentials.get('s1')).toBeNull();
      expect(FakeEventSource.latest().closed).toBe(true);
    });
  });

  describe('joining', () => {
    it('stores the new credentials and connects', async () => {
      const done = store.join('s2', 'Tom');
      backend.expectOne('/api/sessions/s2/join').flush({ sessionId: 's2', participantId: 'p-tom', token: 't-tom' });

      expect(await done).toBe(true);
      expect(credentials.get('s2')?.token).toBe('t-tom');
      expect(FakeEventSource.latest().url).toBe('/api/sessions/s2/events?token=t-tom');
    });

    it('reports a taken name', async () => {
      const done = store.join('s2', 'Tom');
      backend.expectOne('/api/sessions/s2/join').flush({ message: 'Name bereits vergeben' }, { status: 409, statusText: 'Conflict' });

      expect(await done).toBe(false);
      expect(store.error()).toBe('Name bereits vergeben');
      expect(FakeEventSource.instances).toHaveLength(0);
    });
  });

  describe('GitLab issues (scrum master)', () => {
    beforeEach(() => store.connect('s1'));

    it('loads the first page and appends further pages', async () => {
      const first = store.loadIssues();
      backend
        .expectOne((r) => r.url === '/api/sessions/s1/issues' && r.params.get('page') === '1')
        .flush({ issues: [ISSUE], nextPage: 2 });
      await first;
      expect(store.issues().map((i) => i.iid)).toEqual([2]);
      expect(store.hasMoreIssues()).toBe(true);

      const more = store.loadMoreIssues();
      backend
        .expectOne((r) => r.url === '/api/sessions/s1/issues' && r.params.get('page') === '2')
        .flush({ issues: [{ ...ISSUE, iid: 3 }], nextPage: null });
      await more;
      expect(store.issues().map((i) => i.iid)).toEqual([2, 3]);
      expect(store.hasMoreIssues()).toBe(false);
    });

    it('reloading starts again at page 1', async () => {
      const first = store.loadIssues();
      backend.expectOne(() => true).flush({ issues: [ISSUE], nextPage: null });
      await first;

      const again = store.loadIssues();
      backend.expectOne((r) => r.params.get('page') === '1').flush({ issues: [{ ...ISSUE, iid: 5 }], nextPage: null });
      await again;

      expect(store.issues().map((i) => i.iid)).toEqual([5]);
    });
  });
});
