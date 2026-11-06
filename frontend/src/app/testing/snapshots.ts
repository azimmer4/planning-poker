import { Participant, Snapshot } from '../core/models';

export const SARAH: Participant = { id: 'p-sm', name: 'Sarah', role: 'SCRUM_MASTER', hasVoted: false, online: true };
export const TOM: Participant = { id: 'p-tom', name: 'Tom', role: 'DEVELOPER', hasVoted: false, online: true };
export const LENA: Participant = { id: 'p-lena', name: 'Lena', role: 'DEVELOPER', hasVoted: false, online: false };

export const ISSUE = {
  iid: 2,
  title: 'REST-Endpunkt für Benutzerprofil',
  description: 'GET /users/{id} bereitstellen.',
  webUrl: 'http://gitlab-mock/projects/1/-/issues/2',
  labels: ['backend'],
};

/** Builds a snapshot; defaults to the scrum master's view of a fresh session with a selected issue. */
export function snapshot(overrides: Partial<Snapshot> = {}): Snapshot {
  return {
    sessionId: 's1',
    projectId: '1',
    state: 'ISSUE_SELECTED',
    me: SARAH,
    participants: [SARAH, TOM, LENA],
    issue: ISSUE,
    roundNumber: null,
    myVote: null,
    result: null,
    confirmedCard: null,
    history: [],
    allVoted: false,
    ...overrides,
  };
}

export function developerView(overrides: Partial<Snapshot> = {}): Snapshot {
  const { allVoted: _ignored, ...base } = snapshot({ me: TOM });
  return { ...base, ...overrides };
}
