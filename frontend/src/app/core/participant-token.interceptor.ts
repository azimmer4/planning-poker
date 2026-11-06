import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CredentialsService } from './credentials.service';

const SESSION_URL = /^\/api\/sessions\/([^/?]+)(\/[^?]*)?/;

/** Adds the stored X-Participant-Token to every request against a session (except join). */
export const participantTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const match = SESSION_URL.exec(req.url);
  if (!match || match[2] === '/join') {
    return next(req);
  }
  const credentials = inject(CredentialsService).get(decodeURIComponent(match[1]));
  return credentials ? next(req.clone({ setHeaders: { 'X-Participant-Token': credentials.token } })) : next(req);
};
