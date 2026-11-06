import { Component, computed, input, ChangeDetectionStrategy } from '@angular/core';
import { RoundResult } from '../../core/models';
import { findOutliers, formatAverage } from '../../core/result-analysis';

@Component({
  selector: 'app-result-view',
  templateUrl: './result-view.component.html',
  styleUrl: './result-view.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ResultViewComponent {
  readonly result = input.required<RoundResult>();

  protected readonly average = computed(() => formatAverage(this.result().average));
  protected readonly outliers = computed(() => findOutliers(this.result().votes));
  protected readonly consensus = computed(() => this.result().groups.length === 1 && this.result().votes.length >= 2);
}
