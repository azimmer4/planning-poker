import { Component, computed, input, ChangeDetectionStrategy } from '@angular/core';
import { Participant, SessionState } from '../../core/models';

@Component({
  selector: 'app-participant-list',
  templateUrl: './participant-list.component.html',
  styleUrl: './participant-list.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ParticipantListComponent {
  readonly participants = input.required<Participant[]>();
  readonly meId = input<string | null>(null);
  readonly state = input<SessionState | string | null>(null);

  protected readonly voting = computed(() => this.state() === 'VOTING');
  private readonly developers = computed(() => this.participants().filter((p) => p.role === 'DEVELOPER'));
  protected readonly developerCount = computed(() => this.developers().length);
  protected readonly votedCount = computed(() => this.developers().filter((p) => p.hasVoted).length);
}
