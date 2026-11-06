import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CARDS, Card } from '../../core/models';
import { ModeratorControlsComponent } from './moderator-controls.component';

describe('ModeratorControlsComponent', () => {
  let fixture: ComponentFixture<ModeratorControlsComponent>;
  let events: string[];

  const button = (label: string): HTMLButtonElement | undefined =>
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
      (b) => b.textContent?.trim() === label,
    );
  const confirmDeck = (): HTMLButtonElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('[data-testid="confirm-deck"] button'));

  function render(state: string, extra: { allVoted?: boolean; confirmedCard?: Card | null } = {}) {
    fixture.componentRef.setInput('state', state);
    fixture.componentRef.setInput('allVoted', extra.allVoted ?? false);
    fixture.componentRef.setInput('confirmedCard', extra.confirmedCard ?? null);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(ModeratorControlsComponent);
    events = [];
    const c = fixture.componentInstance;
    c.startRound.subscribe(() => events.push('start'));
    c.reveal.subscribe(() => events.push('reveal'));
    c.restart.subscribe(() => events.push('restart'));
    c.confirm.subscribe((card) => events.push('confirm:' + card));
  });

  it('offers nothing without an issue', () => {
    render('NO_ISSUE');
    expect(fixture.nativeElement.querySelectorAll('button')).toHaveLength(0);
  });

  it('releases the selected issue', () => {
    render('ISSUE_SELECTED');
    button('Issue freigeben')!.click();
    expect(events).toEqual(['start']);
    expect(button('Aufdecken')).toBeUndefined();
  });

  it('can reveal at any time while voting', () => {
    render('VOTING');
    button('Aufdecken')!.click();
    expect(events).toEqual(['reveal']);
  });

  it('actively points out when everybody has voted', () => {
    render('VOTING');
    expect(fixture.nativeElement.querySelector('[data-testid="all-voted"]')).toBeNull();

    render('VOTING', { allVoted: true });
    expect(fixture.nativeElement.querySelector('[data-testid="all-voted"]').textContent).toContain('Alle haben geschätzt');
  });

  describe('after reveal', () => {
    beforeEach(() => render('REVEALED'));

    it('starts a new round for the same issue', () => {
      button('Neue Runde')!.click();
      expect(events).toEqual(['restart']);
    });

    it('offers only the deck values as result', () => {
      expect(confirmDeck().map((b) => b.textContent?.trim())).toEqual([...CARDS]);
    });

    it('requires an explicit choice before confirming', () => {
      expect(button('In GitLab übernehmen')!.disabled).toBe(true);

      confirmDeck().find((b) => b.textContent?.trim() === '8')!.click();
      fixture.detectChanges();
      button('In GitLab übernehmen')!.click();

      expect(events).toEqual(['confirm:8']);
    });

    it('forgets the choice when a new round starts', () => {
      confirmDeck().find((b) => b.textContent?.trim() === '8')!.click();
      render('VOTING');
      render('REVEALED');

      expect(button('In GitLab übernehmen')!.disabled).toBe(true);
    });
  });

  it('shows the confirmed value and no further round controls', () => {
    render('CONFIRMED', { confirmedCard: '5' });

    expect(fixture.nativeElement.textContent).toContain('5');
    expect(fixture.nativeElement.textContent).toContain('übernommen');
    expect(button('Aufdecken')).toBeUndefined();
    expect(button('In GitLab übernehmen')).toBeUndefined();
  });
});
