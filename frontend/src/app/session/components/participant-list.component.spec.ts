import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LENA, SARAH, TOM } from '../../testing/snapshots';
import { ParticipantListComponent } from './participant-list.component';

describe('ParticipantListComponent', () => {
  let fixture: ComponentFixture<ParticipantListComponent>;

  const row = (name: string): HTMLElement =>
    Array.from(fixture.nativeElement.querySelectorAll('[data-testid="participant"]') as NodeListOf<HTMLElement>).find(
      (r) => r.textContent?.includes(name),
    )!;

  function render(state: string, participants = [SARAH, { ...TOM, hasVoted: true }, LENA], meId = TOM.id) {
    fixture.componentRef.setInput('participants', participants);
    fixture.componentRef.setInput('meId', meId);
    fixture.componentRef.setInput('state', state);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(ParticipantListComponent);
  });

  it('shows every participant with a visible role', () => {
    render('ISSUE_SELECTED');

    expect(row('Sarah').textContent).toContain('Scrum Master');
    expect(row('Tom').textContent).toContain('Entwickler');
    expect(row('Lena').textContent).toContain('Entwickler');
  });

  it('marks the viewer', () => {
    render('ISSUE_SELECTED');

    expect(row('Tom').textContent).toContain('(du)');
    expect(row('Lena').textContent).not.toContain('(du)');
  });

  it('shows the voting status of developers while voting, never a card', () => {
    render('VOTING');

    expect(row('Tom').textContent).toContain('Hat geschätzt');
    expect(row('Lena').textContent).toContain('Ausstehend');
    expect(row('Sarah').textContent).not.toMatch(/Hat geschätzt|Ausstehend/);
    expect(fixture.nativeElement.querySelector('[data-testid="progress"]').textContent).toContain('1 von 2');
  });

  it('hides the voting status outside a running round', () => {
    render('ISSUE_SELECTED');

    expect(fixture.nativeElement.textContent).not.toMatch(/Hat geschätzt|Ausstehend/);
    expect(fixture.nativeElement.querySelector('[data-testid="progress"]')).toBeNull();
  });

  it('marks offline participants', () => {
    render('VOTING');

    expect(row('Lena').classList).toContain('offline');
    expect(row('Tom').classList).not.toContain('offline');
  });
});
