import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CARDS, Card } from '../../core/models';
import { CardDeckComponent } from './card-deck.component';

describe('CardDeckComponent', () => {
  let fixture: ComponentFixture<CardDeckComponent>;
  let picked: Card[];

  const buttons = () => Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
  const button = (card: string) => buttons().find((b) => b.textContent?.trim() === card)!;

  beforeEach(() => {
    fixture = TestBed.createComponent(CardDeckComponent);
    picked = [];
    fixture.componentInstance.picked.subscribe((c) => picked.push(c));
    fixture.detectChanges();
  });

  it('offers exactly the Fibonacci deck', () => {
    expect(buttons().map((b) => b.textContent?.trim())).toEqual([...CARDS]);
  });

  it('emits the clicked card', () => {
    button('13').click();
    expect(picked).toEqual(['13']);
  });

  it('marks the selected card', () => {
    fixture.componentRef.setInput('selected', '?');
    fixture.detectChanges();

    expect(button('?').getAttribute('aria-pressed')).toBe('true');
    expect(button('5').getAttribute('aria-pressed')).toBe('false');
  });

  it('can be disabled', () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    expect(buttons().every((b) => b.disabled)).toBe(true);
  });
});
