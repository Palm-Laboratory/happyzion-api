alter table admin_account
    drop constraint if exists chk_admin_account_role;

alter table admin_account
    add constraint chk_admin_account_role
        check (role in ('SUPER_ADMIN', 'ADMIN', 'DEVELOPER'));
