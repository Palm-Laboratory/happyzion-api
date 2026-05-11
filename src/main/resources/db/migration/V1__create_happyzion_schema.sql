create or replace function set_current_timestamp_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

create table admin_account (
    id bigserial primary key,
    username varchar(50) not null unique,
    display_name varchar(100) not null,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    active boolean not null default true,
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_admin_account_role
        check (role in ('SUPER_ADMIN', 'ADMIN'))
);

create index idx_admin_account_active_username
    on admin_account(active, username);

create trigger trg_admin_account_updated_at
before update on admin_account
for each row
execute function set_current_timestamp_updated_at();

insert into admin_account (id, username, display_name, password_hash, role, active)
values (
    1,
    'happyzion.admin',
    'Happy Zion 관리자',
    '$2y$10$lcIDIsuVnFhZIGC1s2pvWeM1D5E9wM8IbrqSP0qhP2nUbBzLXf9TW',
    'SUPER_ADMIN',
    true
);

select setval('admin_account_id_seq', 1, true);

create table youtube_channel (
    id bigserial primary key,
    channel_id varchar(64) not null unique,
    title varchar(200) not null,
    is_active boolean not null default true,
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_youtube_channel_updated_at
before update on youtube_channel
for each row
execute function set_current_timestamp_updated_at();

create table youtube_playlist (
    id bigserial primary key,
    channel_id bigint not null references youtube_channel(id) on delete cascade,
    playlist_id varchar(64) not null unique,
    title varchar(200) not null,
    description text,
    thumbnail_url text,
    item_count int not null default 0,
    published_at timestamptz,
    sync_status varchar(16) not null default 'ACTIVE',
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_youtube_playlist_sync_status
        check (sync_status in ('ACTIVE', 'REMOVED'))
);

create index idx_youtube_playlist_channel on youtube_playlist(channel_id);
create index idx_youtube_playlist_sync_status on youtube_playlist(sync_status);

create trigger trg_youtube_playlist_updated_at
before update on youtube_playlist
for each row
execute function set_current_timestamp_updated_at();

create table youtube_video (
    id bigserial primary key,
    channel_id bigint not null references youtube_channel(id) on delete cascade,
    video_id varchar(64) not null unique,
    title varchar(300) not null,
    description text,
    thumbnail_url text,
    published_at timestamptz,
    duration_seconds int,
    content_form varchar(16) not null default 'LONGFORM',
    privacy_status varchar(16) not null default 'PUBLIC',
    sync_status varchar(16) not null default 'ACTIVE',
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_youtube_video_content_form
        check (content_form in ('LONGFORM', 'SHORTFORM')),
    constraint chk_youtube_video_privacy_status
        check (privacy_status in ('PUBLIC', 'UNLISTED', 'PRIVATE')),
    constraint chk_youtube_video_sync_status
        check (sync_status in ('ACTIVE', 'REMOVED'))
);

create index idx_youtube_video_channel on youtube_video(channel_id);
create index idx_youtube_video_published_at on youtube_video(published_at desc);
create index idx_youtube_video_content_form on youtube_video(content_form);
create index idx_youtube_video_sync_status on youtube_video(sync_status);

create trigger trg_youtube_video_updated_at
before update on youtube_video
for each row
execute function set_current_timestamp_updated_at();

create table youtube_playlist_item (
    id bigserial primary key,
    playlist_id bigint not null references youtube_playlist(id) on delete cascade,
    video_id bigint not null references youtube_video(id) on delete cascade,
    position int not null,
    added_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_youtube_playlist_item_playlist_video unique (playlist_id, video_id),
    constraint uk_youtube_playlist_item_playlist_position unique (playlist_id, position)
);

create index idx_youtube_playlist_item_video on youtube_playlist_item(video_id);
create index idx_youtube_playlist_item_playlist on youtube_playlist_item(playlist_id, position);

create trigger trg_youtube_playlist_item_updated_at
before update on youtube_playlist_item
for each row
execute function set_current_timestamp_updated_at();

create table youtube_video_meta (
    id bigserial primary key,
    video_id bigint not null unique references youtube_video(id) on delete cascade,
    display_title varchar(300),
    preacher_name varchar(120),
    display_published_at timestamptz,
    hidden boolean not null default false,
    scripture_reference varchar(200),
    scripture_body text,
    message_body text,
    summary text,
    thumbnail_override_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_youtube_video_meta_hidden on youtube_video_meta(hidden);
create index idx_youtube_video_meta_display_published_at on youtube_video_meta(display_published_at desc);

create trigger trg_youtube_video_meta_updated_at
before update on youtube_video_meta
for each row
execute function set_current_timestamp_updated_at();

create table menu_item (
    id bigserial primary key,
    parent_id bigint references menu_item(id) on delete cascade,
    type varchar(32) not null,
    status varchar(16) not null default 'PUBLISHED',
    label varchar(100) not null,
    label_customized boolean not null default false,
    slug_customized boolean not null default false,
    slug varchar(100) not null,
    static_page_key varchar(100),
    board_key varchar(100),
    playlist_id bigint unique references youtube_playlist(id) on delete cascade,
    external_url text,
    open_in_new_tab boolean not null default false,
    depth int not null default 0,
    path text not null default '',
    sort_order int not null default 0,
    is_auto boolean not null default false,
    playlist_content_form varchar(16),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_menu_item_type
        check (type in (
            'STATIC',
            'BOARD',
            'FOLDER',
            'YOUTUBE_PLAYLIST_GROUP',
            'YOUTUBE_PLAYLIST',
            'EXTERNAL_LINK'
        )),
    constraint chk_menu_item_status
        check (status in ('DRAFT', 'PUBLISHED', 'HIDDEN', 'ARCHIVED')),
    constraint chk_menu_item_playlist_content_form
        check (
            playlist_content_form is null
            or playlist_content_form in ('LONGFORM', 'SHORTFORM')
        )
);

create unique index uq_menu_item_root_slug
    on menu_item(slug)
    where parent_id is null;

create unique index uq_menu_item_sibling_slug
    on menu_item(parent_id, slug)
    where parent_id is not null;

create index idx_menu_item_parent_sort on menu_item(parent_id, sort_order);
create index idx_menu_item_status on menu_item(status);
create index idx_menu_item_type on menu_item(type);
create index idx_menu_item_playlist on menu_item(playlist_id);

create trigger trg_menu_item_updated_at
before update on menu_item
for each row
execute function set_current_timestamp_updated_at();

create table menu_revision (
    id bigserial primary key,
    snapshot jsonb not null,
    summary varchar(200),
    created_by bigint references admin_account(id),
    created_at timestamptz not null default now()
);

create index idx_menu_revision_created_at on menu_revision(created_at desc);

create table board (
    id bigserial primary key,
    slug varchar(100) not null unique,
    menu_id bigint references menu_item(id) on delete set null,
    title varchar(200) not null,
    type varchar(32) not null,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_board_type
        check (type in ('NOTICE', 'BULLETIN', 'ALBUM', 'GENERAL'))
);

create unique index uq_board_menu_id
    on board(menu_id)
    where menu_id is not null;

create trigger trg_board_updated_at
before update on board
for each row
execute function set_current_timestamp_updated_at();

create table post (
    id bigserial primary key,
    board_id bigint not null references board(id) on delete cascade,
    menu_id bigint not null references menu_item(id),
    title varchar(200) not null,
    content_json jsonb not null,
    content_html text,
    author_id bigint not null references admin_account(id),
    published_at timestamptz,
    is_public boolean not null default true,
    is_pinned boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    view_count bigint not null default 0
);

create index idx_post_board_id on post(board_id);
create index idx_post_author_id on post(author_id);
create index idx_post_board_public_published_at on post(board_id, is_public, published_at desc);
create index idx_post_menu_id_created_at on post(menu_id, created_at desc, id desc);
create index idx_post_menu_id_public_created_at on post(menu_id, is_public, created_at desc, id desc);
create index idx_post_board_public_pinned_created_at on post(board_id, is_public, is_pinned desc, created_at desc, id desc);
create index idx_post_menu_public_pinned_created_at on post(menu_id, is_public, is_pinned desc, created_at desc, id desc);
create index idx_post_menu_public_pinned_created_at_view_count
    on post(menu_id, is_public, is_pinned desc, created_at desc, id desc, view_count desc);

create trigger trg_post_updated_at
before update on post
for each row
execute function set_current_timestamp_updated_at();

create table post_asset (
    id bigserial primary key,
    post_id bigint references post(id) on delete cascade,
    uploaded_by_actor_id bigint not null references admin_account(id),
    kind varchar(32) not null,
    original_filename varchar(255) not null,
    stored_path text not null,
    byte_size bigint not null,
    detached_at timestamptz,
    mime_type varchar(120),
    width integer,
    height integer,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_post_asset_stored_path unique (stored_path),
    constraint chk_post_asset_kind
        check (kind in ('INLINE_IMAGE', 'FILE_ATTACHMENT'))
);

create index idx_post_asset_post_id on post_asset(post_id);
create index idx_post_asset_uploaded_by_actor_id on post_asset(uploaded_by_actor_id);
create index idx_post_asset_detached_at
    on post_asset(detached_at)
    where post_id is null;

create trigger trg_post_asset_updated_at
before update on post_asset
for each row
execute function set_current_timestamp_updated_at();

create table upload_token (
    id bigserial primary key,
    actor_id bigint not null references admin_account(id),
    max_byte_size bigint not null,
    token_hash varchar(128) not null unique,
    asset_kind varchar(32) not null,
    allowed_mime_types jsonb not null default '[]'::jsonb,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_upload_token_asset_kind
        check (asset_kind in ('INLINE_IMAGE', 'FILE_ATTACHMENT'))
);

create index idx_upload_token_expires_at on upload_token(expires_at);

create trigger trg_upload_token_updated_at
before update on upload_token
for each row
execute function set_current_timestamp_updated_at();

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

insert into menu_item (
    id,
    parent_id,
    type,
    status,
    label,
    label_customized,
    slug_customized,
    slug,
    static_page_key,
    board_key,
    playlist_id,
    external_url,
    open_in_new_tab,
    depth,
    path,
    sort_order,
    is_auto,
    playlist_content_form
)
values
    (1, null, 'FOLDER', 'PUBLISHED', '교회 소개', false, false, 'about', null, null, null, null, false, 0, '', 0, false, null),
    (2, 1, 'STATIC', 'PUBLISHED', '인사말/비전', false, false, 'greeting', 'about.greeting', null, null, null, false, 1, '', 0, false, null),
    (3, 1, 'STATIC', 'PUBLISHED', '교회 이야기', false, false, 'church-story', 'about.church-story', null, null, null, false, 1, '', 1, false, null),
    (4, 1, 'STATIC', 'PUBLISHED', '부흥 조직도', false, false, 'revival-organization', 'about.revival-organization', null, null, null, false, 1, '', 2, false, null),
    (5, 1, 'STATIC', 'PUBLISHED', '예배 안내', false, false, 'service-times', 'about.service-times', null, null, null, false, 1, '', 3, false, null),
    (6, 1, 'STATIC', 'PUBLISHED', '선교 이력', false, false, 'mission-history', 'about.mission-history', null, null, null, false, 1, '', 4, false, null),
    (7, 1, 'STATIC', 'PUBLISHED', '오시는 길', false, false, 'location', 'about.location', null, null, null, false, 1, '', 5, false, null),
    (8, 1, 'STATIC', 'PUBLISHED', '온라인 헌금', false, false, 'online-giving', 'about.online-giving', null, null, null, false, 1, '', 6, false, null),
    (9, null, 'FOLDER', 'PUBLISHED', '선교 사역', false, false, 'mission', null, null, null, null, false, 0, '', 1, false, null),
    (10, 9, 'BOARD', 'PUBLISHED', '필리핀 선교', false, false, 'philippines', null, 'mission-philippines', null, null, false, 1, '', 0, false, null),
    (11, 9, 'BOARD', 'PUBLISHED', '인도네시아 선교', false, false, 'indonesia', null, 'mission-indonesia', null, null, false, 1, '', 1, false, null),
    (12, null, 'FOLDER', 'PUBLISHED', '행복이 가득한', false, false, 'happy', null, null, null, null, false, 0, '', 2, false, null),
    (13, 12, 'BOARD', 'PUBLISHED', '2026년', false, false, '2026', null, 'happy-2026', null, null, false, 1, '', 0, false, null);

select setval('menu_item_id_seq', 13, true);

insert into board (id, slug, menu_id, title, type, description)
values
    (1, 'happy-2026', 13, '2026년', 'GENERAL', null),
    (2, 'mission-philippines', 10, '필리핀 선교', 'GENERAL', null),
    (3, 'mission-indonesia', 11, '인도네시아 선교', 'GENERAL', null);

select setval('board_id_seq', 3, true);

with recursive menu_paths as (
    select id, parent_id, concat('/', id, '/') as computed_path, 0 as computed_depth
    from menu_item
    where parent_id is null
    union all
    select child.id, child.parent_id, concat(parent.computed_path, child.id, '/'), parent.computed_depth + 1
    from menu_item child
    join menu_paths parent on child.parent_id = parent.id
)
update menu_item menu
set path = menu_paths.computed_path,
    depth = menu_paths.computed_depth
from menu_paths
where menu.id = menu_paths.id;
