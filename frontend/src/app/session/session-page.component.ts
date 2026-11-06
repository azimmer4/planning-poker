import { Component, DestroyRef, OnInit, computed, effect, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Card } from '../core/models';
import { SessionStore } from '../core/session.store';
import { CardDeckComponent } from './components/card-deck.component';
import { HistoryListComponent } from './components/history-list.component';
import { IssueCardComponent } from './components/issue-card.component';
import { IssuePickerComponent } from './components/issue-picker.component';
import { ModeratorControlsComponent } from './components/moderator-controls.component';
import { ParticipantListComponent } from './components/participant-list.component';
import { ResultViewComponent } from './components/result-view.component';

const COPY_FEEDBACK_MS = 2000;

@Component({
  selector: 'app-session-page',
  providers: [SessionStore],
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CardDeckComponent,
    HistoryListComponent,
    IssueCardComponent,
    IssuePickerComponent,
    ModeratorControlsComponent,
    ParticipantListComponent,
    ResultViewComponent,
  ],
  templateUrl: './session-page.component.html',
  styleUrl: './session-page.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class SessionPageComponent implements OnInit {
  protected readonly store = inject(SessionStore);
  private readonly router = inject(Router);
  protected readonly sessionId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  protected readonly joinLink = `${location.origin}/s/${encodeURIComponent(this.sessionId)}`;
  protected readonly joinForm = inject(FormBuilder).nonNullable.group({ name: ['', [Validators.required, Validators.pattern(/\S/)]] });
  protected readonly reconnecting = computed(() => this.store.status() === 'reconnecting');

  private issuesLoaded = false;
  protected readonly copyState = signal<'idle' | 'copied' | 'failed'>('idle');
  private copyResetTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.copyResetTimer));
    effect(() => {
      if (this.store.isScrumMaster() && !this.issuesLoaded) {
        this.issuesLoaded = true;
        void this.store.loadIssues();
      }
    });
  }

  ngOnInit(): void {
    this.store.connect(this.sessionId);
  }

  protected join(): Promise<boolean> {
    return this.store.join(this.sessionId, this.joinForm.getRawValue().name.trim());
  }

  protected vote(card: Card): Promise<boolean> {
    return this.store.vote(card);
  }

  protected async leave(): Promise<void> {
    if (await this.store.leave()) {
      await this.router.navigate(['/']);
    }
  }

  protected async copyLink(): Promise<void> {
    const copied = (await this.writeToClipboard(this.joinLink)) || this.copyViaExecCommand(this.joinLink);
    this.copyState.set(copied ? 'copied' : 'failed');
    clearTimeout(this.copyResetTimer);
    if (copied) {
      this.copyResetTimer = setTimeout(() => this.copyState.set('idle'), COPY_FEEDBACK_MS);
    }
  }

  private writeToClipboard(text: string): Promise<boolean> {
    // Only available in secure contexts (https or localhost)
    if (!navigator.clipboard) return Promise.resolve(false);
    try {
      return navigator.clipboard.writeText(text).then(
        () => true,
        () => false,
      );
    } catch {
      return Promise.resolve(false);
    }
  }

  private copyViaExecCommand(text: string): boolean {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try {
      return document.execCommand?.('copy') ?? false;
    } catch {
      return false;
    } finally {
      area.remove();
    }
  }
}
