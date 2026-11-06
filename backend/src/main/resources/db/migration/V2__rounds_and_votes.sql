create table estimation_round
(
    id         uuid primary key,
    session_id uuid        not null references poker_session (id) on delete cascade,
    issue_iid  integer     not null,
    number     integer     not null,
    status     varchar(32) not null,
    created_at timestamptz not null
);

create index estimation_round_session_issue_idx on estimation_round (session_id, issue_iid);

create table vote
(
    id             uuid primary key,
    round_id       uuid        not null references estimation_round (id) on delete cascade,
    participant_id uuid        not null references participant (id) on delete cascade,
    card           varchar(16) not null,
    voted_at       timestamptz not null,
    unique (round_id, participant_id)
);

alter table poker_session
    add column current_round_id uuid references estimation_round (id) on delete set null;
