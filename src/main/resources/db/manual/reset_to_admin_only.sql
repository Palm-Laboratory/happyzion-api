do $$
declare
    drop_statement text;
begin
    for drop_statement in
        select format('drop table if exists %I.%I cascade', schemaname, tablename)
        from pg_tables
        where schemaname = 'public'
          and tablename not in ('admin_account', 'flyway_schema_history')
    loop
        execute drop_statement;
    end loop;

    if to_regclass('public.flyway_schema_history') is not null then
        execute 'truncate table public.flyway_schema_history';
    end if;
end $$;
