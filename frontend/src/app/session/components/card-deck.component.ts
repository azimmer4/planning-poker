import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { CARDS, Card } from '../../core/models';

@Component({
  selector: 'app-card-deck',
  templateUrl: './card-deck.component.html',
  styleUrl: './card-deck.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class CardDeckComponent {
  readonly cards = input<readonly Card[]>(CARDS);
  readonly selected = input<Card | null>(null);
  readonly disabled = input(false);
  readonly picked = output<Card>();
}
