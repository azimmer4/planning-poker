import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoundResult } from '../../core/models';
import { ResultViewComponent } from './result-view.component';

describe('ResultViewComponent', () => {
  let fixture: ComponentFixture<ResultViewComponent>;

  const text = (testId: string): string =>
    (fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const all = (testId: string): string[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`) as NodeListOf<HTMLElement>).map(
      (e) => Array.from(e.children).map((c) => c.textContent!.trim()).join(' '),
    );

  function render(result: RoundResult) {
    fixture.componentRef.setInput('result', result);
    fixture.detectChanges();
  }

  const split: RoundResult = {
    votes: [
      { participantId: 'a', name: 'Tom', card: '5' },
      { participantId: 'b', name: 'Lena', card: '8' },
      { participantId: 'c', name: 'Max', card: '5' },
    ],
    groups: [
      { card: '5', count: 2 },
      { card: '8', count: 1 },
    ],
    average: 6,
    mostFrequent: '5',
  };

  beforeEach(() => {
    fixture = TestBed.createComponent(ResultViewComponent);
  });

  it('shows card and name per participant', () => {
    render(split);

    expect(all('vote')).toEqual(['5 Tom', '8 Lena', '5 Max']);
  });

  it('shows how many participants chose each value', () => {
    render(split);

    expect(all('group')).toEqual(['5 2 ×', '8 1 ×']);
  });

  it('shows average (German format) and most frequent value as orientation', () => {
    render({ ...split, average: 6.5 });

    expect(text('average')).toContain('6,5');
    expect(text('most-frequent')).toContain('5');
  });

  it('shows a dash when there is no numeric average', () => {
    render({ votes: [{ participantId: 'a', name: 'Tom', card: '☕' }], groups: [{ card: '☕', count: 1 }], average: null, mostFrequent: '☕' });

    expect(text('average')).toContain('–');
  });

  it('handles a round without any vote', () => {
    render({ votes: [], groups: [], average: null, mostFrequent: null });

    expect(text('empty')).toContain('Keine Schätzungen');
  });

  it('celebrates consensus', () => {
    render({ ...split, votes: split.votes.map((v) => ({ ...v, card: '5' as const })), groups: [{ card: '5', count: 3 }], average: 5 });

    expect(text('consensus')).toContain('Konsens');
  });

  it('highlights outliers when the estimates spread widely', () => {
    render({
      votes: [
        { participantId: 'a', name: 'Tom', card: '3' },
        { participantId: 'b', name: 'Lena', card: '3' },
        { participantId: 'c', name: 'Max', card: '21' },
      ],
      groups: [
        { card: '3', count: 2 },
        { card: '21', count: 1 },
      ],
      average: 9,
      mostFrequent: '3',
    });

    const outliers = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="vote"].outlier') as NodeListOf<HTMLElement>);
    expect(outliers.map((e) => e.textContent)).toEqual([expect.stringContaining('Max')]);
    expect(text('consensus')).toBe('');
  });

  it('does not flag neighbouring cards as outliers', () => {
    render(split);

    expect(fixture.nativeElement.querySelectorAll('.outlier')).toHaveLength(0);
  });
});
