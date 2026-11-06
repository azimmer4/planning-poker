import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ISSUE } from '../../testing/snapshots';
import { IssueCardComponent } from './issue-card.component';

describe('IssueCardComponent', () => {
  let fixture: ComponentFixture<IssueCardComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(IssueCardComponent);
  });

  it('shows title, description, labels and links to GitLab', () => {
    fixture.componentRef.setInput('issue', ISSUE);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('h2')!.textContent).toContain('#2 REST-Endpunkt für Benutzerprofil');
    expect(el.textContent).toContain('GET /users/{id} bereitstellen.');
    expect(el.textContent).toContain('backend');
    expect(el.querySelector('a')!.getAttribute('href')).toBe(ISSUE.webUrl);
  });

  it('renders the description as text, never as HTML', () => {
    fixture.componentRef.setInput('issue', { ...ISSUE, description: '<img src=x onerror=alert(1)>' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('copes with a missing description', () => {
    fixture.componentRef.setInput('issue', { ...ISSUE, description: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Keine Beschreibung');
  });
});
