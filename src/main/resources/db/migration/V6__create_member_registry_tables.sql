create table member (
    id bigserial primary key,
    name varchar(100) not null,
    name_en varchar(100),
    baptism_name varchar(100),
    sex varchar(1) not null,
    birth_date date not null,
    birth_calendar varchar(10) not null,
    phone varchar(30) not null,
    emergency_phone varchar(30),
    emergency_relation varchar(50),
    email varchar(150),
    address varchar(200) not null,
    address_detail varchar(200),
    job varchar(120),
    photo_path varchar(255),
    cell_id varchar(60),
    cell_label varchar(120),
    status varchar(32) not null,
    faith_stage varchar(32) not null,
    office varchar(32) not null default 'LAY',
    office_appointed_at date,
    registered_at date not null,
    memo text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_member_sex
        check (sex in ('M', 'F')),
    constraint chk_member_birth_calendar
        check (birth_calendar in ('SOLAR', 'LUNAR')),
    constraint chk_member_status
        check (status in ('ACTIVE', 'NEW', 'RESTING', 'LONG_ABSENT', 'TRANSFERRED_OUT', 'DECEASED', 'REMOVED')),
    constraint chk_member_faith_stage
        check (faith_stage in ('SEEKER', 'NEW_COMER', 'SETTLED', 'GROWING', 'DISCIPLE', 'MINISTER', 'LEADER')),
    constraint chk_member_office
        check (office in ('LAY', 'DEACON_TEMP', 'DEACON', 'GWONSA', 'ELDER', 'ELDER_EMERITUS', 'EVANGELIST', 'PASTOR'))
);

create index idx_member_registered_at on member(registered_at desc, id desc);
create index idx_member_status on member(status);
create index idx_member_faith_stage on member(faith_stage);
create index idx_member_cell_id on member(cell_id);
create index idx_member_name on member(name);
create index idx_member_phone on member(phone);

create trigger trg_member_updated_at
before update on member
for each row
execute function set_current_timestamp_updated_at();

create table member_faith (
    member_id bigint primary key references member(id) on delete cascade,
    confess_date date,
    learning_date date,
    baptism_date date,
    baptism_place varchar(120),
    baptism_officiant varchar(120),
    confirmation_date date,
    previous_church varchar(120),
    transferred_in_at date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_member_faith_updated_at
before update on member_faith
for each row
execute function set_current_timestamp_updated_at();

create table member_family (
    id bigserial primary key,
    member_id bigint not null references member(id) on delete cascade,
    related_member_id bigint references member(id) on delete set null,
    external_name varchar(100),
    relation varchar(20) not null,
    relation_detail varchar(50),
    is_head boolean not null default false,
    sex varchar(1),
    phone varchar(30),
    birth_date date,
    group_note varchar(200),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_member_family_relation
        check (relation in ('SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'OTHER')),
    constraint chk_member_family_sex
        check (sex is null or sex in ('M', 'F'))
);

create index idx_member_family_member_id on member_family(member_id, is_head desc, id asc);

create trigger trg_member_family_updated_at
before update on member_family
for each row
execute function set_current_timestamp_updated_at();

create table member_service (
    id bigserial primary key,
    member_id bigint not null references member(id) on delete cascade,
    department varchar(120) not null,
    team varchar(120),
    role varchar(120) not null,
    started_at date not null,
    ended_at date,
    schedule varchar(200),
    note varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_member_service_member_id on member_service(member_id, ended_at asc, started_at desc, id desc);

create trigger trg_member_service_updated_at
before update on member_service
for each row
execute function set_current_timestamp_updated_at();

create table member_training (
    id bigserial primary key,
    member_id bigint not null references member(id) on delete cascade,
    program_name varchar(120) not null,
    completed_at date not null,
    note varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_member_training_member_id on member_training(member_id, completed_at desc, id desc);

create trigger trg_member_training_updated_at
before update on member_training
for each row
execute function set_current_timestamp_updated_at();

create table member_tag (
    id bigserial primary key,
    member_id bigint not null references member(id) on delete cascade,
    tag varchar(80) not null,
    created_at timestamptz not null default now()
);

create index idx_member_tag_member_id on member_tag(member_id, tag);

create table attendance_service_date (
    id bigserial primary key,
    service_date date not null,
    service_type varchar(50) not null,
    note varchar(200),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_attendance_service_date unique (service_date, service_type)
);

create index idx_attendance_service_date_date on attendance_service_date(service_date desc, id desc);

create trigger trg_attendance_service_date_updated_at
before update on attendance_service_date
for each row
execute function set_current_timestamp_updated_at();

create table attendance_record (
    id bigserial primary key,
    service_date_id bigint not null references attendance_service_date(id) on delete cascade,
    member_id bigint not null references member(id) on delete cascade,
    status varchar(20) not null,
    reason varchar(200),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_attendance_record unique (service_date_id, member_id),
    constraint chk_attendance_record_status
        check (status in ('ATTEND', 'ABSENT', 'EXCUSED', 'ONLINE'))
);

create index idx_attendance_record_member_id on attendance_record(member_id, service_date_id);

create trigger trg_attendance_record_updated_at
before update on attendance_record
for each row
execute function set_current_timestamp_updated_at();

create table member_event_log (
    id bigserial primary key,
    member_id bigint not null references member(id) on delete cascade,
    type varchar(40) not null,
    payload jsonb,
    actor_id bigint not null references admin_account(id),
    created_at timestamptz not null default now(),
    constraint chk_member_event_type
        check (type in (
            'REGISTERED',
            'STATUS_CHANGED',
            'STAGE_CHANGED',
            'OFFICE_CHANGED',
            'CELL_MOVED',
            'SERVICE_ASSIGNED',
            'SERVICE_ENDED',
            'TRAINING_COMPLETED',
            'ADDRESS_CHANGED',
            'PHOTO_CHANGED',
            'FAMILY_LINKED',
            'FAMILY_UNLINKED'
        ))
);

create index idx_member_event_log_member_id on member_event_log(member_id, created_at desc, id desc);
