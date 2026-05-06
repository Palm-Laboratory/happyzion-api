alter table post
    add column if not exists view_count bigint not null default 0;

create index if not exists idx_post_menu_public_pinned_created_at_view_count
    on post(menu_id, is_public, is_pinned desc, created_at desc, id desc, view_count desc);
