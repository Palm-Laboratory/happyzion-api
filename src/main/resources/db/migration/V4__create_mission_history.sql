create table mission_year (
    id bigserial primary key,
    year varchar(20) not null,
    caption varchar(200) not null,
    tone varchar(10),
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_mission_year_tone
        check (tone in ('gold', 'red'))
);

create index idx_mission_year_sort_order on mission_year(sort_order, id);

create trigger trg_mission_year_updated_at
before update on mission_year
for each row
execute function set_current_timestamp_updated_at();

create table mission_entry (
    id bigserial primary key,
    year_id bigint not null references mission_year(id) on delete cascade,
    month varchar(3),
    place varchar(200) not null,
    is_first boolean not null default false,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_mission_entry_month
        check (month in ('Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'))
);

create index idx_mission_entry_year_id on mission_entry(year_id, sort_order, id);

create trigger trg_mission_entry_updated_at
before update on mission_entry
for each row
execute function set_current_timestamp_updated_at();
