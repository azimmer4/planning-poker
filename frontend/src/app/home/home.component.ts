import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { lastValueFrom } from 'rxjs';
import { CredentialsService } from '../core/credentials.service';
import { Credentials } from '../core/models';
import { SessionApi } from '../core/session-api.service';
import { errorMessage } from '../core/session.store';

const NOT_BLANK = Validators.pattern(/\S/);

@Component({
  selector: 'app-home',
  imports: [ReactiveFormsModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class HomeComponent {
  private readonly api = inject(SessionApi);
  private readonly credentials = inject(CredentialsService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly createForm = this.fb.group({
    projectId: ['', [Validators.required, NOT_BLANK]],
    issueIid: [''],
    name: ['', [Validators.required, NOT_BLANK]],
  });

  protected readonly joinForm = this.fb.group({
    session: ['', [Validators.required, NOT_BLANK]],
    name: ['', [Validators.required, NOT_BLANK]],
  });

  protected create(): Promise<void> {
    const { projectId, issueIid, name } = this.createForm.getRawValue();
    const iid = String(issueIid ?? '').trim();
    return this.enter(this.api.create({ projectId: projectId.trim(), issueIid: iid ? Number(iid) : null, name: name.trim() }));
  }

  protected join(): Promise<void> {
    const { session, name } = this.joinForm.getRawValue();
    return this.enter(this.api.join(sessionIdFrom(session), name.trim()));
  }

  private async enter(request: ReturnType<SessionApi['create']>): Promise<void> {
    this.busy.set(true);
    try {
      const credentials: Credentials = await lastValueFrom(request);
      this.credentials.save(credentials);
      await this.router.navigate(['/s', credentials.sessionId]);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Accepts a plain session id or a join link like https://host/s/{id}. */
export function sessionIdFrom(input: string): string {
  const trimmed = input.trim();
  const match = /\/s\/([^/?#]+)/.exec(trimmed);
  return decodeURIComponent(match ? match[1] : trimmed);
}
