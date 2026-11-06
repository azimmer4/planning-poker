import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HistoryEntry } from '../../core/models';
import { HistoryListComponent } from './history-list.component';

describe('HistoryListComponent', () => {
  let fixture: ComponentFixture<HistoryListComponent>;

  const entry = (overrides: Partial<HistoryEntry>): HistoryEntry => ({
    issueIid: 2,
    issueTitle: 'REST-Endpunkt',
    roundNumber: 1,
    votes: [
      { name: 'Tom', card: '5' },
      { name: 'Lena', card: '8' },
    ],
    groups: [],
    average: 6.5,
    mostFrequent: '5',
    confirmedCard: null,
    ...overrides,
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HistoryListComponent);
  });

  it('lists finished rounds with their votes', () => {
    fixture.componentRef.setInput('entries', [entry({}), entry({ roundNumber: 2, confirmedCard: '5' })]);
    fixture.detectChanges();

    const rows = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="history-entry"]') as NodeListOf<HTMLElement>).map(
      (r) => r.textContent!.replace(/\s+/g, ' '),
    );
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('#2 REST-Endpunkt');
    expect(rows[0]).toContain('Runde 1');
    expect(rows[0]).toContain('Tom: 5');
    expect(rows[0]).toContain('Lena: 8');
    expect(rows[0]).not.toContain('Übernommen');
    expect(rows[1]).toContain('Übernommen: 5');
  });

  it('renders nothing without finished rounds', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });
});
