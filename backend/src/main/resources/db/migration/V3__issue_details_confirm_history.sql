-- Issue details are cached when the issue is selected, so snapshots never call GitLab.
alter table poker_session
    add column issue_title       varchar(1024),
    add column issue_description text,
    add column issue_web_url     varchar(2048),
    add column issue_labels      text[];

alter table estimation_round
    add column issue_title    varchar(1024),
    add column confirmed_card varchar(16);

-- Votes keep the voter's name so the session history survives participants leaving.
alter table vote add column voter_name varchar(40);
update vote v set voter_name = p.name from participant p where p.id = v.participant_id;
alter table vote alter column voter_name set not null;
alter table vote alter column participant_id drop not null;
alter table vote drop constraint vote_participant_id_fkey;
alter table vote
    add constraint vote_participant_id_fkey foreign key (participant_id) references participant (id) on delete set null;
