/** Wire types of the planning-poker backend (see README, section "API"). */

export const CARDS = ['0', '1', '2', '3', '5', '8', '13', '21', '34', '?', '☕'] as const;
export type Card = (typeof CARDS)[number];

export type Role = 'SCRUM_MASTER' | 'DEVELOPER';
export type SessionState = 'NO_ISSUE' | 'ISSUE_SELECTED' | 'VOTING' | 'REVEALED' | 'CONFIRMED';

export interface Credentials {
  sessionId: string;
  participantId: string;
  token: string;
}

export interface Participant {
  id: string;
  name: string;
  role: Role;
  hasVoted: boolean;
  online: boolean;
}

export interface Issue {
  iid: number;
  title: string;
  description: string | null;
  webUrl: string | null;
  labels: string[];
}

export interface IssuePage {
  issues: Issue[];
  nextPage: number | null;
}

export interface CardCount {
  card: Card;
  count: number;
}

export interface VoteView {
  participantId: string;
  name: string;
  card: Card;
}

export interface RoundResult {
  votes: VoteView[];
  groups: CardCount[];
  average: number | null;
  mostFrequent: Card | null;
}

export interface HistoryEntry {
  issueIid: number;
  issueTitle: string | null;
  roundNumber: number;
  votes: { name: string; card: Card }[];
  groups: CardCount[];
  average: number | null;
  mostFrequent: Card | null;
  confirmedCard: Card | null;
}

export interface Snapshot {
  sessionId: string;
  projectId: string;
  state: SessionState;
  me: Participant;
  participants: Participant[];
  issue: Issue | null;
  roundNumber: number | null;
  myVote: Card | null;
  result: RoundResult | null;
  confirmedCard: Card | null;
  history: HistoryEntry[];
  /** Only sent to the scrum master. */
  allVoted?: boolean;
}

export interface CreateSessionRequest {
  projectId: string;
  issueIid: number | null;
  name: string;
}
