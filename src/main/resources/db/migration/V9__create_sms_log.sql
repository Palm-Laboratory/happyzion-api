create table sms_log (
    id                bigserial primary key,
    aligo_msg_id      varchar(64),
    msg_type          varchar(8)  not null,
    sender            varchar(20) not null,
    title             varchar(80),
    total_count       integer     not null,
    success_count     integer     not null default 0,
    error_count       integer     not null default 0,
    test_mode         boolean     not null default false,
    requested_by      bigint      not null references admin_account(id) on delete restrict,
    requested_at      timestamptz not null default now(),
    aligo_result_code integer,
    aligo_message     text,
    constraint chk_sms_log_msg_type check (msg_type in ('SMS','LMS','MMS'))
);
create index idx_sms_log_requested_at on sms_log(requested_at desc, id desc);
create index idx_sms_log_aligo_msg_id on sms_log(aligo_msg_id);

create table sms_log_recipient (
    id              bigserial primary key,
    sms_log_id      bigint      not null references sms_log(id) on delete cascade,
    phone_enc       text        not null,
    phone_hash      varchar(64) not null,
    receiver_name_enc text,
    message_enc     text        not null,
    church_member_id bigint references church_member(id) on delete set null,
    status          varchar(16) not null default 'PENDING',
    aligo_send_state varchar(40),
    constraint chk_sms_log_recipient_status
        check (status in ('PENDING','SENT','FAILED','UNKNOWN'))
);
create index idx_sms_log_recipient_log on sms_log_recipient(sms_log_id);
create index idx_sms_log_recipient_phone_hash on sms_log_recipient(phone_hash);
