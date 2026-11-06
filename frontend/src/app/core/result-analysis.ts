import { Card, VoteView } from './models';

const NUMERIC: readonly Card[] = ['0', '1', '2', '3', '5', '8', '13', '21', '34'];

/** Minimum distance (in deck positions) from the median card for a vote to count as outlier. */
export const OUTLIER_DISTANCE = 2;

/**
 * Votes far away from the group's median card (measured in deck steps, so 3→8 is two steps).
 * Needs at least three numeric votes; `?` and `☕` are never outliers.
 */
export function findOutliers(votes: VoteView[]): Set<string> {
  const positions = votes
    .map((v) => ({ id: v.participantId, index: NUMERIC.indexOf(v.card) }))
    .filter((v) => v.index >= 0);
  if (positions.length < 3) {
    return new Set();
  }
  const sorted = positions.map((p) => p.index).sort((a, b) => a - b);
  const median = sorted[Math.floor((sorted.length - 1) / 2)];
  return new Set(positions.filter((p) => Math.abs(p.index - median) >= OUTLIER_DISTANCE).map((p) => p.id));
}

export function formatAverage(average: number | null): string {
  return average === null ? '–' : average.toLocaleString('de-DE', { maximumFractionDigits: 1 });
}
