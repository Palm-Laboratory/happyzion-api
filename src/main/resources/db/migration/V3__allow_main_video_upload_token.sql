alter table upload_token
    drop constraint chk_upload_token_asset_kind;

alter table upload_token
    add constraint chk_upload_token_asset_kind
        check (asset_kind in ('INLINE_IMAGE', 'FILE_ATTACHMENT', 'MAIN_VIDEO'));
