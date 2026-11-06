create table poker_session
(
    id                 uuid primary key,
    project_id         varchar(255) not null,
    state              varchar(32)  not null,
    selected_issue_iid integer,
    created_at         timestamptz  not null
);

create table participant
(
    id         uuid primary key,
    session_id uuid        not null references poker_session (id) on delete cascade,
    name       varchar(40) not null,
    role       varchar(32) not null,
    token_hash varchar(64) not null unique,
    joined_at  timestamptz not null
);

create unique index participant_session_name_uq on participant (session_id, lower(name));
create index participant_session_idx on participant (session_id);
