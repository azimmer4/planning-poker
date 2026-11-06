import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { HistoryEntry } from '../../core/models';
import { formatAverage } from '../../core/result-analysis';

@Component({
  selector: 'app-history-list',
  templateUrl: './history-list.component.html',
  styleUrl: './history-list.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class HistoryListComponent {
  readonly entries = input.required<HistoryEntry[]>();

  protected average(entry: HistoryEntry): string {
    return formatAverage(entry.average);
  }
}
