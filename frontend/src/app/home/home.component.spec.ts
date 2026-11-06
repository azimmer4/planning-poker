import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { CredentialsService } from '../core/credentials.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let backend: HttpTestingController;
  let router: Router;

  const el = (testId: string): HTMLElement => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  function type(testId: string, value: string) {
    const input = el(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  /** Lets pending promise continuations (native async/await is not tracked by zone.js) run. */
  async function settle() {
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(HomeComponent);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  describe('create session (scrum master)', () => {
    it('needs project and name', () => {
      expect((el('create-submit') as HTMLButtonElement).disabled).toBe(true);
      type('create-project', '1');
      type('create-name', 'Sarah');
      expect((el('create-submit') as HTMLButtonElement).disabled).toBe(false);
    });

    it('creates the session, remembers the credentials and opens it', async () => {
      type('create-project', ' 1 ');
      type('create-issue', '2');
      type('create-name', ' Sarah ');
      el('create-submit').click();

      const req = backend.expectOne({ method: 'POST', url: '/api/sessions' });
      expect(req.request.body).toEqual({ projectId: '1', issueIid: 2, name: 'Sarah' });
      req.flush({ sessionId: 's1', participantId: 'p1', token: 't1' });
      await settle();

      expect(TestBed.inject(CredentialsService).get('s1')?.token).toBe('t1');
      expect(router.navigate).toHaveBeenCalledWith(['/s', 's1']);
    });

    it('the issue is optional', () => {
      type('create-project', '1');
      type('create-name', 'Sarah');
      el('create-submit').click();

      expect(backend.expectOne('/api/sessions').request.body.issueIid).toBeNull();
    });

    it('shows backend errors', async () => {
      type('create-project', '999');
      type('create-name', 'Sarah');
      el('create-submit').click();
      backend.expectOne('/api/sessions').flush({ message: 'GitLab-Projekt 999 nicht gefunden' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(el('error').textContent).toContain('GitLab-Projekt 999 nicht gefunden');
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('join session (developer)', () => {
    it.each([
      ['a session id', 's1'],
      ['a join link', 'http://localhost:4200/s/s1'],
    ])('joins via %s', async (_label, input) => {
      type('join-session', input);
      type('join-name', 'Tom');
      el('join-submit').click();

      const req = backend.expectOne({ method: 'POST', url: '/api/sessions/s1/join' });
      expect(req.request.body).toEqual({ name: 'Tom' });
      req.flush({ sessionId: 's1', participantId: 'p2', token: 't2' });
      await settle();

      expect(TestBed.inject(CredentialsService).get('s1')?.token).toBe('t2');
      expect(router.navigate).toHaveBeenCalledWith(['/s', 's1']);
    });

    it('needs session and name', () => {
      type('join-session', 's1');
      expect((el('join-submit') as HTMLButtonElement).disabled).toBe(true);
    });
  });
});
