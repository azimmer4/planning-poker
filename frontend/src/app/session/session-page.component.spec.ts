import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { CredentialsService } from '../core/credentials.service';
import { EVENT_SOURCE_FACTORY } from '../core/session-events';
import { FakeEventSource } from '../testing/fake-event-source';
import { ISSUE, developerView, snapshot } from '../testing/snapshots';
import { SessionPageComponent } from './session-page.component';

describe('SessionPageComponent', () => {
  let fixture: ComponentFixture<SessionPageComponent>;
  let backend: HttpTestingController;
  let credentials: CredentialsService;
  let router: Router;

  const el = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const text = (): string => fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  const button = (label: string): HTMLButtonElement | undefined =>
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
      (b) => b.textContent?.trim() === label,
    );

  function create() {
    fixture = TestBed.createComponent(SessionPageComponent);
    fixture.detectChanges();
  }

  function push(s = snapshot()) {
    FakeEventSource.latest().emitSnapshot(s);
    fixture.detectChanges();
  }

  /** Lets pending promise continuations (native async/await is not tracked by zone.js) run. */
  async function settle() {
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  /** The scrum master's page loads the GitLab issue list once. */
  async function answerIssueList() {
    backend.expectOne((r) => r.url === '/api/sessions/s1/issues').flush({ issues: [ISSUE], nextPage: null });
    await settle();
  }

  beforeEach(() => {
    localStorage.clear();
    FakeEventSource.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => new FakeEventSource(url) },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 's1' }) } } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    credentials = TestBed.inject(CredentialsService);
    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  describe('without credentials (opened via join link)', () => {
    beforeEach(() => create());

    it('asks for a name and joins', async () => {
      expect(FakeEventSource.instances).toHaveLength(0);
      const input = el('join-name') as HTMLInputElement;
      input.value = 'Tom';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      el('join-submit')!.click();

      backend.expectOne('/api/sessions/s1/join').flush({ sessionId: 's1', participantId: 'p-tom', token: 't-tom' });
      await settle();

      expect(credentials.get('s1')?.token).toBe('t-tom');
      expect(FakeEventSource.latest().url).toBe('/api/sessions/s1/events?token=t-tom');
    });
  });

  describe('as scrum master', () => {
    beforeEach(() => {
      credentials.save({ sessionId: 's1', participantId: 'p-sm', token: 't-sm' });
      create();
    });

    it('shows the join link, the issue picker and the controls', async () => {
      push();
      await answerIssueList();

      expect(el('join-link')!.textContent).toContain('/s/s1');
      expect(el('issue-picker')).not.toBeNull();
      expect(button('Issue freigeben')).toBeDefined();
      expect(el('card-deck')).toBeNull();
    });

    describe('copying the join link', () => {
      const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
      const copyButton = (): HTMLButtonElement => el('copy-link') as HTMLButtonElement;

      function mockClipboard(writeText: ((text: string) => Promise<void>) | undefined) {
        Object.defineProperty(navigator, 'clipboard', {
          configurable: true,
          value: writeText ? { writeText } : undefined,
        });
      }

      afterEach(() => {
        jest.useRealTimers();
        if (original) Object.defineProperty(navigator, 'clipboard', original);
        else delete (navigator as { clipboard?: unknown }).clipboard;
      });

      it('confirms the copy visibly and resets after a moment', async () => {
        const writeText = jest.fn().mockResolvedValue(undefined);
        mockClipboard(writeText);
        push();
        await answerIssueList();
        jest.useFakeTimers();

        copyButton().click();
        for (let i = 0; i < 5; i++) await Promise.resolve();
        fixture.detectChanges();

        expect(writeText).toHaveBeenCalledWith(expect.stringContaining('/s/s1'));
        expect(copyButton().textContent).toContain('Kopiert');
        expect(copyButton().classList).toContain('copied');

        jest.advanceTimersByTime(2000);
        fixture.detectChanges();
        expect(copyButton().textContent).toContain('Link kopieren');
        expect(copyButton().classList).not.toContain('copied');
      });

      it('falls back to execCommand without the clipboard API (plain http)', async () => {
        mockClipboard(undefined);
        const execCommand = jest.fn().mockReturnValue(true);
        (document as { execCommand?: unknown }).execCommand = execCommand;
        push();
        await answerIssueList();

        copyButton().click();
        await settle();

        expect(execCommand).toHaveBeenCalledWith('copy');
        expect(copyButton().textContent).toContain('Kopiert');
      });

      it('tells the user when copying is not possible', async () => {
        mockClipboard(() => Promise.reject(new Error('denied')));
        (document as { execCommand?: unknown }).execCommand = jest.fn().mockReturnValue(false);
        push();
        await answerIssueList();

        copyButton().click();
        await settle();

        expect(el('copy-failed')!.textContent).toContain('manuell');
        expect(copyButton().classList).not.toContain('copied');
      });
    });

    it('releases the issue', async () => {
      push();
      await answerIssueList();
      button('Issue freigeben')!.click();

      backend.expectOne({ method: 'POST', url: '/api/sessions/s1/rounds' }).flush(null, { status: 204, statusText: 'No Content' });
    });

    it('is told when everybody has voted', async () => {
      push(snapshot({ state: 'VOTING', roundNumber: 1, allVoted: true }));
      await answerIssueList();

      expect(el('all-voted')).not.toBeNull();
    });

    it('selects another issue from GitLab', async () => {
      push(snapshot({ state: 'NO_ISSUE', issue: null }));
      await answerIssueList();
      (el('issue-picker')!.querySelector('[data-testid="issue-option"] button') as HTMLButtonElement).click();

      expect(backend.expectOne({ method: 'PUT', url: '/api/sessions/s1/issue' }).request.body).toEqual({ issueIid: 2 });
    });

    it('shows backend errors, e.g. GitLab being unavailable', async () => {
      push(snapshot({ state: 'REVEALED', result: { votes: [], groups: [], average: null, mostFrequent: null } }));
      await answerIssueList();
      (el('confirm-deck')!.querySelectorAll('button')[4] as HTMLButtonElement).click();
      fixture.detectChanges();
      button('In GitLab übernehmen')!.click();

      backend.expectOne('/api/sessions/s1/confirm').flush({ message: 'GitLab nicht erreichbar' }, { status: 502, statusText: 'Bad Gateway' });
      await settle();

      expect(el('error')!.textContent).toContain('GitLab nicht erreichbar');
    });
  });

  describe('as developer', () => {
    beforeEach(() => {
      credentials.save({ sessionId: 's1', participantId: 'p-tom', token: 't-tom' });
      create();
    });

    it('waits for the release without seeing the issue', () => {
      push(developerView({ state: 'ISSUE_SELECTED', issue: null }));

      expect(text()).toContain('Warte auf Freigabe');
      expect(el('card-deck')).toBeNull();
      expect(el('issue-picker')).toBeNull();
      expect(button('Issue freigeben')).toBeUndefined();
    });

    it('sees the released issue and votes covertly', () => {
      push(developerView({ state: 'VOTING', roundNumber: 1 }));

      expect(text()).toContain('REST-Endpunkt für Benutzerprofil');
      (Array.from(el('card-deck')!.querySelectorAll('button')) as HTMLButtonElement[]).find((b) => b.textContent?.trim() === '5')!.click();

      expect(backend.expectOne({ method: 'PUT', url: '/api/sessions/s1/votes' }).request.body).toEqual({ card: '5' });
    });

    it('sees the own vote as selected card', () => {
      push(developerView({ state: 'VOTING', roundNumber: 1, myVote: '8' }));

      const selected = el('card-deck')!.querySelector('[aria-pressed="true"]');
      expect(selected!.textContent?.trim()).toBe('8');
    });

    it('sees the revealed cards', () => {
      push(
        developerView({
          state: 'REVEALED',
          result: { votes: [{ participantId: 'p-lena', name: 'Lena', card: '13' }], groups: [{ card: '13', count: 1 }], average: 13, mostFrequent: '13' },
        }),
      );

      expect(el('result')).not.toBeNull();
      expect(text()).toContain('Lena');
      expect(el('card-deck')).toBeNull();
    });

    it('can leave the session', async () => {
      push(developerView());
      button('Sitzung verlassen')!.click();

      backend.expectOne('/api/sessions/s1/leave').flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(credentials.get('s1')).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/']);
    });
  });

  describe('connection problems', () => {
    beforeEach(() => {
      credentials.save({ sessionId: 's1', participantId: 'p-tom', token: 't-tom' });
      create();
    });

    it('shows a reconnect hint', () => {
      push(developerView());
      FakeEventSource.latest().fail(false);
      fixture.detectChanges();

      expect(el('connection')!.textContent).toContain('Verbindung');
    });

    it('offers to join again when the token is no longer valid', () => {
      FakeEventSource.latest().fail(true);
      backend.expectOne('/api/sessions/s1').flush({ message: 'x' }, { status: 401, statusText: 'Unauthorized' });
      fixture.detectChanges();

      expect(el('join-name')).not.toBeNull();
    });

    it('reports an unknown session', () => {
      FakeEventSource.latest().fail(true);
      backend.expectOne('/api/sessions/s1').flush({ message: 'x' }, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();

      expect(text()).toContain('Sitzung nicht gefunden');
    });

    it('closes the stream when the page is left', () => {
      fixture.destroy();
      expect(FakeEventSource.latest().closed).toBe(true);
    });
  });
});
