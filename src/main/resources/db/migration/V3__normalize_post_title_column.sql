DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'post'
          AND column_name = 'title'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE post
            ALTER COLUMN title TYPE varchar(200)
            USING convert_from(title, 'UTF8');
    END IF;
END $$;
