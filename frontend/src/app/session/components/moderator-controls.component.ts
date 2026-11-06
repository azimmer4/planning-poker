import { Component, input, linkedSignal, output, ChangeDetectionStrategy } from '@angular/core';
import { Card, SessionState } from '../../core/models';
import { CardDeckComponent } from './card-deck.component';

@Component({
  selector: 'app-moderator-controls',
  imports: [CardDeckComponent],
  templateUrl: './moderator-controls.component.html',
  styleUrl: './moderator-controls.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ModeratorControlsComponent {
  readonly state = input.required<SessionState | string>();
  readonly allVoted = input(false);
  readonly confirmedCard = input<Card | null>(null);

  readonly startRound = output<void>();
  readonly reveal = output<void>();
  readonly restart = output<void>();
  readonly confirm = output<Card>();

  /** The result picked by the scrum master; reset whenever the state changes. */
  protected readonly chosen = linkedSignal<SessionState | string, Card | null>({ source: this.state, computation: () => null });

  protected confirmChosen(): void {
    const card = this.chosen();
    if (card !== null) {
      this.confirm.emit(card);
    }
  }
}
