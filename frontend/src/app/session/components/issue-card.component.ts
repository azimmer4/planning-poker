import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { Issue } from '../../core/models';

@Component({
  selector: 'app-issue-card',
  templateUrl: './issue-card.component.html',
  styleUrl: './issue-card.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class IssueCardComponent {
  readonly issue = input.required<Issue>();
}
