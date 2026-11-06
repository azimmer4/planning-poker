package com.example.poker.session;

public enum SessionState {
    /** No issue selected yet. */
    NO_ISSUE,
    /** Scrum master selected an issue; not yet released to developers. */
    ISSUE_SELECTED,
    /** Issue released, developers are voting. */
    VOTING,
    /** Cards are revealed. */
    REVEALED,
    /** The agreed card was written to GitLab. */
    CONFIRMED
}
