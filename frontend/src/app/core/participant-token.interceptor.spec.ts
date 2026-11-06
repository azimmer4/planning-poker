import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CredentialsService } from './credentials.service';
import { participantTokenInterceptor } from './participant-token.interceptor';

describe('participantTokenInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([participantTokenInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(CredentialsService).save({ sessionId: 's1', participantId: 'p1', token: 'secret' });
  });

  afterEach(() => backend.verify());

  it.each(['/api/sessions/s1', '/api/sessions/s1/votes', '/api/sessions/s1/issues'])(
    'adds the participant token of the session to %s',
    (url) => {
      http.get(url).subscribe();
      expect(backend.expectOne(url).request.headers.get('X-Participant-Token')).toBe('secret');
    },
  );

  it('sends no token for sessions without credentials', () => {
    http.get('/api/sessions/other').subscribe();
    expect(backend.expectOne('/api/sessions/other').request.headers.has('X-Participant-Token')).toBe(false);
  });

  it.each(['/api/sessions', '/api/sessions/s1/join'])('sends no token to %s', (url) => {
    http.post(url, {}).subscribe();
    expect(backend.expectOne(url).request.headers.has('X-Participant-Token')).toBe(false);
  });

  it('leaves foreign URLs untouched', () => {
    http.get('/assets/x.json').subscribe();
    expect(backend.expectOne('/assets/x.json').request.headers.has('X-Participant-Token')).toBe(false);
  });
});
