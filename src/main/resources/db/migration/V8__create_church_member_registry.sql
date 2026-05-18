create table church_member (
    id                       bigserial primary key,

    name_enc                 text not null,
    name_hash                varchar(64) not null,
    phone_enc                text not null,
    phone_hash               varchar(64) not null,
    phone_last4_hash         varchar(64),
    email_enc                text,
    address_enc              text not null,
    address_detail_enc       text,
    job_enc                  text,
    memo_enc                 text,
    birth_date_enc           text not null,

    sex                      varchar(1)   not null,
    birth_calendar           varchar(10)  not null,
    photo_asset_id           bigint references post_asset(id),
    cell_label               varchar(120),
    status                   varchar(32)  not null,
    faith_stage              varchar(32),
    office                   varchar(32)  not null default 'LAY',
    office_appointed_at      date,
    registered_at            date         not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),

    constraint uq_church_member_photo_asset_id unique (photo_asset_id),
    constraint chk_church_member_sex            check (sex in ('M','F')),
    constraint chk_church_member_birth_calendar check (birth_calendar in ('SOLAR','LUNAR')),
    constraint chk_church_member_status         check (status in (
        'ACTIVE','NEW','RESTING','LONG_ABSENT','TRANSFERRED_OUT','DECEASED','REMOVED')),
    constraint chk_church_member_office         check (office in (
        'LAY','DEACON_TEMP','DEACON','GWONSA','ELDER','ELDER_EMERITUS','EVANGELIST','PASTOR')),
    constraint chk_church_member_faith_stage    check (faith_stage is null or faith_stage in (
        'SEEKER','NEW_COMER','SETTLED','GROWING','DISCIPLE','MINISTER','LEADER'))
);

create index idx_church_member_name_hash       on church_member(name_hash);
create index idx_church_member_phone_hash      on church_member(phone_hash);
create index idx_church_member_phone_last4     on church_member(phone_last4_hash);
create index idx_church_member_registered_at   on church_member(registered_at desc, id desc);
create index idx_church_member_status          on church_member(status);
create index idx_church_member_faith_stage     on church_member(faith_stage);

create trigger trg_church_member_updated_at
before update on church_member
for each row
execute function set_current_timestamp_updated_at();

create table church_member_faith (
    church_member_id              bigint primary key
                                  references church_member(id) on delete cascade,
    confess_date_enc              text,
    learning_date_enc             text,
    baptism_date_enc              text,
    baptism_place_enc             text,
    baptism_officiant_enc         text,
    confirmation_date_enc         text,
    previous_church_enc           text,
    transferred_in_at_enc         text,
    created_at                    timestamptz not null default now(),
    updated_at                    timestamptz not null default now()
);

create trigger trg_church_member_faith_updated_at
before update on church_member_faith
for each row
execute function set_current_timestamp_updated_at();

create table church_member_audit_log (
    id                bigserial primary key,
    church_member_id  bigint not null references church_member(id) on delete restrict,
    actor_id          bigint not null references admin_account(id) on delete restrict,
    action            varchar(20) not null,
    diff_enc          text,
    created_at        timestamptz not null default now(),
    constraint chk_church_member_audit_action check (action in ('CREATE','UPDATE','DELETE'))
);

create index idx_church_member_audit_member_id
    on church_member_audit_log(church_member_id, created_at desc, id desc);

alter table post_asset
    drop constraint chk_post_asset_kind;
alter table post_asset
    add constraint chk_post_asset_kind
    check (kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MEMBER_PHOTO'));

alter table upload_token
    drop constraint chk_upload_token_asset_kind;
alter table upload_token
    add constraint chk_upload_token_asset_kind
    check (asset_kind in ('INLINE_IMAGE','FILE_ATTACHMENT','MAIN_VIDEO','MEMBER_PHOTO'));
