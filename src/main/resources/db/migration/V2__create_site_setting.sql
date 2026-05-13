create table if not exists site_setting (
    setting_key varchar(80) primary key,
    setting_value text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

drop trigger if exists trg_site_setting_updated_at on site_setting;

create trigger trg_site_setting_updated_at
before update on site_setting
for each row
execute function set_current_timestamp_updated_at();
