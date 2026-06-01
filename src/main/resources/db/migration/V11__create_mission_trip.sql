create table mission_trip (
    id                    bigserial primary key,
    title                 varchar(200) not null,
    country               varchar(100) not null,
    start_date            date         not null,
    end_date              date,
    type                  varchar(32)  not null default 'SHORT_TERM',
    status                varchar(32)  not null default 'PLANNED',
    leader_label          varchar(120),
    budget                bigint,
    description           text,
    cover_photo_asset_id  bigint references post_asset(id) on delete set null,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),

    constraint chk_mission_trip_type   check (type   in ('SHORT_TERM','MEDICAL','VISION_TRIP','SUPPORT_VISIT','OTHER')),
    constraint chk_mission_trip_status check (status in ('PLANNED','RECRUITING','ONGOING','COMPLETED','CANCELLED')),
    constraint chk_mission_trip_dates  check (end_date is null or end_date >= start_date)
);

create index idx_mission_trip_start_date on mission_trip(start_date desc, id desc);
create index idx_mission_trip_status     on mission_trip(status);

create trigger trg_mission_trip_updated_at
before update on mission_trip
for each row
execute function set_current_timestamp_updated_at();

create table mission_trip_participant (
    id                   bigserial primary key,
    mission_trip_id      bigint      not null references mission_trip(id) on delete cascade,
    church_member_id     bigint               references church_member(id) on delete restrict,
    external_name        varchar(120),
    role                 varchar(32) not null default 'MEMBER',
    participation_status varchar(32) not null default 'CONFIRMED',
    note                 text,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),

    constraint chk_mission_participant_identity
        check (
            (church_member_id is not null and external_name is null)
            or
            (church_member_id is null and external_name is not null)
        ),
    constraint chk_mission_participant_role   check (role   in ('LEADER','MEMBER','INTERPRETER','MEDICAL','SUPPORTER')),
    constraint chk_mission_participant_status check (participation_status in ('APPLIED','CONFIRMED','CANCELLED'))
);

-- 교인 중복 참가 방지 (외부인은 대상 아님)
create unique index uq_mission_trip_participant_member
    on mission_trip_participant(mission_trip_id, church_member_id)
    where church_member_id is not null;

create index idx_mission_trip_participant_trip   on mission_trip_participant(mission_trip_id);
create index idx_mission_trip_participant_member on mission_trip_participant(church_member_id);

create trigger trg_mission_trip_participant_updated_at
before update on mission_trip_participant
for each row
execute function set_current_timestamp_updated_at();
