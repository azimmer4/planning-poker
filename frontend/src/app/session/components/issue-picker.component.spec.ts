import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ISSUE } from '../../testing/snapshots';
import { IssuePickerComponent } from './issue-picker.component';

describe('IssuePickerComponent', () => {
  let fixture: ComponentFixture<IssuePickerComponent>;
  let picked: number[];
  let loadMore: number;

  const items = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="issue-option"]'));
  const button = (label: string): HTMLButtonElement | undefined =>
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find((b) =>
      b.textContent?.includes(label),
    );

  function render(state = 'ISSUE_SELECTED', hasMore = false, activeIid: number | null = 2) {
    fixture.componentRef.setInput('issues', [ISSUE, { ...ISSUE, iid: 3, title: 'Passwort zurücksetzen' }]);
    fixture.componentRef.setInput('activeIid', activeIid);
    fixture.componentRef.setInput('hasMore', hasMore);
    fixture.componentRef.setInput('state', state);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(IssuePickerComponent);
    picked = [];
    loadMore = 0;
    fixture.componentInstance.pick.subscribe((iid) => picked.push(iid));
    fixture.componentInstance.loadMore.subscribe(() => loadMore++);
  });

  it('lists the open GitLab issues and marks the active one', () => {
    render();

    expect(items().map((i) => i.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      expect.stringContaining('#2 REST-Endpunkt für Benutzerprofil'),
      expect.stringContaining('#3 Passwort zurücksetzen'),
    ]);
    expect(items()[0].classList).toContain('active');
  });

  it('labels the active issue so it stays readable and is announced', () => {
    render();

    const active = items()[0].querySelector('button')!;
    expect(active.getAttribute('aria-current')).toBe('true');
    expect(items()[0].querySelector('[data-testid="active-badge"]')!.textContent).toContain('Aktiv');
    expect(items()[1].querySelector('[data-testid="active-badge"]')).toBeNull();
  });

  it('selects another issue', () => {
    render();
    items()[1].querySelector('button')!.click();
    expect(picked).toEqual([3]);
  });

  it('cannot re-select the active issue', () => {
    render();
    expect(items()[0].querySelector('button')!.disabled).toBe(true);
  });

  it('loads more issues on demand', () => {
    render('ISSUE_SELECTED', false);
    expect(button('Weitere laden')).toBeUndefined();

    render('ISSUE_SELECTED', true);
    button('Weitere laden')!.click();
    expect(loadMore).toBe(1);
  });

  it('warns that switching discards the running estimation', () => {
    render('ISSUE_SELECTED');
    expect(fixture.nativeElement.querySelector('[data-testid="switch-warning"]')).toBeNull();

    render('VOTING');
    expect(fixture.nativeElement.querySelector('[data-testid="switch-warning"]').textContent).toContain('verworfen');
  });
});
