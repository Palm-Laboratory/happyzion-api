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

create table board_type (
    id bigserial primary key,
    key varchar(32) not null unique,
    label varchar(100) not null,
    description text,
    sort_order integer not null default 0
);

insert into board_type (key, label, description, sort_order)
values
    ('NOTICE', '공지사항', '공지와 안내 게시판', 0),
    ('BULLETIN', '주보', '주보 게시판', 1),
    ('ALBUM', '행사 앨범', '사진 중심 행사 앨범 게시판', 2),
    ('GENERAL', '자유게시판', '일반 게시판', 3);

create table board (
    id bigserial primary key,
    slug varchar(100) not null unique,
    menu_id bigint references menu_item(id) on delete set null,
    board_type_id bigint not null references board_type(id),
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
    updated_at timestamptz not null default now()
);

create index idx_post_board_id on post(board_id);
create index idx_post_author_id on post(author_id);
create index idx_post_board_public_published_at on post(board_id, is_public, published_at desc);
create index idx_post_menu_id_created_at on post(menu_id, created_at desc, id desc);
create index idx_post_menu_id_public_created_at on post(menu_id, is_public, created_at desc, id desc);
create index idx_post_board_public_pinned_created_at on post(board_id, is_public, is_pinned desc, created_at desc, id desc);
create index idx_post_menu_public_pinned_created_at on post(menu_id, is_public, is_pinned desc, created_at desc, id desc);

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
    board_id bigint references board(id) on delete cascade,
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

create index idx_upload_token_board_id on upload_token(board_id);
create index idx_upload_token_expires_at on upload_token(expires_at);

create trigger trg_upload_token_updated_at
before update on upload_token
for each row
execute function set_current_timestamp_updated_at();

with root_about as (
    insert into menu_item (parent_id, type, status, label, slug, static_page_key, board_key, external_url, open_in_new_tab, depth, path, sort_order, is_auto)
    values (null, 'STATIC', 'PUBLISHED', '교회 소개', 'about', 'about', null, null, false, 0, '', 0, false)
    returning id
),
root_videos as (
    insert into menu_item (parent_id, type, status, label, slug, static_page_key, board_key, external_url, open_in_new_tab, depth, path, sort_order, is_auto)
    values (null, 'YOUTUBE_PLAYLIST_GROUP', 'PUBLISHED', '예배 영상', 'videos', null, null, null, false, 0, '', 1, false)
    returning id
),
root_mission as (
    insert into menu_item (parent_id, type, status, label, slug, static_page_key, board_key, external_url, open_in_new_tab, depth, path, sort_order, is_auto)
    values (null, 'STATIC', 'PUBLISHED', '선교', 'mission', 'mission', null, null, false, 0, '', 2, false)
    returning id
),
root_events as (
    insert into menu_item (parent_id, type, status, label, slug, static_page_key, board_key, external_url, open_in_new_tab, depth, path, sort_order, is_auto)
    values (null, 'STATIC', 'PUBLISHED', '교회 행사', 'events', 'events', null, null, false, 0, '', 3, false)
    returning id
)
select 1;

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
