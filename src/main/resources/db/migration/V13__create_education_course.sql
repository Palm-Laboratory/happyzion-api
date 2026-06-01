create table education_course (
    id                    bigserial primary key,
    title                 varchar(200) not null,
    category              varchar(32)  not null default 'NEW_MEMBER',
    start_date            date         not null,
    end_date              date,
    status                varchar(32)  not null default 'PLANNED',
    instructor_label      varchar(120),
    location              varchar(200),
    description           text,
    cover_photo_asset_id  bigint references post_asset(id) on delete set null,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),

    constraint chk_education_course_category check (category in (
        'NEW_MEMBER','BIBLE_STUDY','DISCIPLESHIP','MINISTRY_TRAINING',
        'LEADERSHIP','TEACHER_TRAINING','OTHER'
    )),
    constraint chk_education_course_status check (status in ('PLANNED','RECRUITING','ONGOING','COMPLETED','CANCELLED')),
    constraint chk_education_course_dates  check (end_date is null or end_date >= start_date)
);

create index idx_education_course_start_date on education_course(start_date desc, id desc);
create index idx_education_course_status     on education_course(status);

create trigger trg_education_course_updated_at
before update on education_course
for each row
execute function set_current_timestamp_updated_at();

create table education_enrollment (
    id                   bigserial primary key,
    education_course_id  bigint      not null references education_course(id) on delete cascade,
    church_member_id     bigint               references church_member(id) on delete restrict,
    external_name        varchar(120),
    role                 varchar(32) not null default 'STUDENT',
    enrollment_status    varchar(32) not null default 'ENROLLED',
    note                 text,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),

    constraint chk_education_enrollment_identity
        check (
            (church_member_id is not null and external_name is null)
            or
            (church_member_id is null and external_name is not null)
        ),
    constraint chk_education_enrollment_role   check (role in ('STUDENT','LEADER','ASSISTANT')),
    constraint chk_education_enrollment_status check (enrollment_status in ('APPLIED','ENROLLED','COMPLETED','DROPPED'))
);

create unique index uq_education_enrollment_member
    on education_enrollment(education_course_id, church_member_id)
    where church_member_id is not null;

create index idx_education_enrollment_course on education_enrollment(education_course_id);
create index idx_education_enrollment_member on education_enrollment(church_member_id);

create trigger trg_education_enrollment_updated_at
before update on education_enrollment
for each row
execute function set_current_timestamp_updated_at();
