drop index if exists idx_upload_token_board_id;

alter table upload_token
    drop column if exists board_id;
