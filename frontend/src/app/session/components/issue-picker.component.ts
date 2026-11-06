import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { Issue, SessionState } from '../../core/models';

@Component({
  selector: 'app-issue-picker',
  templateUrl: './issue-picker.component.html',
  styleUrl: './issue-picker.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class IssuePickerComponent {
  readonly issues = input.required<Issue[]>();
  readonly activeIid = input<number | null>(null);
  readonly hasMore = input(false);
  readonly state = input<SessionState | string | null>(null);

  readonly pick = output<number>();
  readonly loadMore = output<void>();
}
